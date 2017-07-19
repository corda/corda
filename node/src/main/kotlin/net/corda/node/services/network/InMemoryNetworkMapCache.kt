package net.corda.node.services.network

import net.corda.core.internal.VisibleForTesting
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.PartyInfo
import net.corda.core.schemas.NodeInfoSchemaV1
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.DEFAULT_SESSION_ID
import net.corda.node.services.api.NetworkCacheError
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.createMessage
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.jetbrains.exposed.sql.transactions.TransactionManager
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Extremely simple in-memory cache of the network map.
 *
 * @param serviceHub an optional service hub from which we'll take the identity service. We take a service hub rather
 * than the identity service directly, as this avoids problems with service start sequence (network map cache
 * and identity services depend on each other). Should always be provided except for unit test cases.
 */
// TODO Split into InMemory- and Persistent- NetworkMapCache
@ThreadSafe
open class InMemoryNetworkMapCache(
        val loadNetworkCacheDB: Boolean = false,
        private val serviceHub: ServiceHubInternal?) : SingletonSerializeAsToken(), NetworkMapCacheInternal {
    companion object {
        val logger = loggerFor<InMemoryNetworkMapCache>()
    }

    private var sessionFactory: SessionFactory? = null
    private var registeredForPush = false

    override val partyNodes: List<NodeInfo> get() = registeredNodes.map { it.value }
    override val networkMapNodes: List<NodeInfo> get() = getNodesWithService(NetworkMapService.type)
    private val _changed = PublishSubject.create<MapChange>()
    // We use assignment here so that multiple subscribers share the same wrapped Observable.
    override val changed: Observable<MapChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MapChange> get() = _changed.bufferUntilDatabaseCommit()

    private val _registrationFuture = openFuture<Void?>()
    override val mapServiceRegistered: CordaFuture<Void?> get() = _registrationFuture
    protected val registeredNodes: MutableMap<PublicKey, NodeInfo> = Collections.synchronizedMap(HashMap())
    private var _loadDBSuccess: Boolean = false
    override val loadDBSuccess get() = _loadDBSuccess

    init {
        serviceHub?.schemaService?.let {
            sessionFactory = HibernateConfiguration(it).sessionFactoryForRegisteredSchemas()
        }
        if (loadNetworkCacheDB) {
            loadFromDB()
        }
    }

    override fun getPartyInfo(party: Party): PartyInfo? {
        val node = registeredNodes[party.owningKey]
        if (node != null) {
            return PartyInfo.Node(node)
        }
        for ((_, value) in registeredNodes) {
            for (service in value.advertisedServices) {
                if (service.identity.party == party) {
                    return PartyInfo.Service(service)
                }
            }
        }
        return null
    }

    // TODO DB query, for now registeredNodes will be just populated as before
    override fun getNodeByLegalIdentityKey(identityKey: PublicKey): NodeInfo? = registeredNodes[identityKey]
    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        val wellKnownParty = if (serviceHub != null) {
            serviceHub.identityService.partyFromAnonymous(party)
        } else {
            party
        }

        return wellKnownParty?.let {
            getNodeByLegalIdentityKey(it.owningKey)
        }
    }

    override fun track(): DataFeed<List<NodeInfo>, MapChange> {
        synchronized(_changed) {
            return DataFeed(partyNodes, _changed.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun addMapService(network: MessagingService, networkMapAddress: SingleMessageRecipient, subscribe: Boolean,
                               ifChangedSinceVer: Int?): CordaFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            network.addMessageHandler(NetworkMapService.PUSH_TOPIC, DEFAULT_SESSION_ID) { message, _ ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val ackMessage = network.createMessage(NetworkMapService.PUSH_ACK_TOPIC, DEFAULT_SESSION_ID,
                            NetworkMapService.UpdateAcknowledge(req.mapVersion, network.myAddress).serialize().bytes)
                    network.send(ackMessage, req.replyTo)
                    processUpdatePush(req)
                } catch(e: NodeMapError) {
                    logger.warn("Failure during node map update due to bad update: ${e.javaClass.name}")
                } catch(e: Exception) {
                    logger.error("Exception processing update from network map service", e)
                }
            }
            registeredForPush = true
        }

        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, network.myAddress)
        val future = network.sendRequest<FetchMapResponse>(NetworkMapService.FETCH_TOPIC, req, networkMapAddress).map { (nodes) ->
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            nodes?.forEach { processRegistration(it) }
            Unit
        }
        _registrationFuture.captureLater(future.map { null })

        return future
    }

    override fun addNode(node: NodeInfo) {
        synchronized(_changed) {
            val previousNode = registeredNodes.put(node.legalIdentity.owningKey, node)
            if (previousNode == null) {
                updateInfoDB(node)
                changePublisher.onNext(MapChange.Added(node))
            } else if (previousNode != node) {
                changePublisher.onNext(MapChange.Modified(node, previousNode))
            }
        }
    }

    override fun removeNode(node: NodeInfo) {
        synchronized(_changed) {
            registeredNodes.remove(node.legalIdentity.owningKey)
            removeInfoDB(node)
            changePublisher.onNext(MapChange.Removed(node))
        }
    }

    /**
     * Unsubscribes from updates from the given map service.
     * @param service the network map service to listen to updates from.
     */
    override fun deregisterForUpdates(network: MessagingService, service: NodeInfo): CordaFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.SubscribeRequest(false, network.myAddress)
        // `network.getAddressOfParty(partyInfo)` is a work-around for MockNetwork and InMemoryMessaging to get rid of SingleMessageRecipient in NodeInfo.
        val address = network.getAddressOfParty(PartyInfo.Node(service))
        val future = network.sendRequest<SubscribeResponse>(NetworkMapService.SUBSCRIPTION_TOPIC, req, address).map {
            if (it.confirmed) Unit else throw NetworkCacheError.DeregistrationFailed()
        }
        _registrationFuture.captureLater(future.map { null })
        return future
    }

    fun processUpdatePush(req: NetworkMapService.Update) {
        try {
            val reg = req.wireReg.verified()
            processRegistration(reg)
        } catch (e: SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
    }

    private fun processRegistration(reg: NodeRegistration) {
        // TODO: Implement filtering by sequence number, so we only accept changes that are
        // more recent than the latest change we've processed.
        when (reg.type) {
            AddOrRemove.ADD -> addNode(reg.node)
            AddOrRemove.REMOVE -> removeNode(reg.node)
        }
    }

    @VisibleForTesting
    override fun runWithoutMapService() {
        _registrationFuture.set(null)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Changes related to NetworkMap redesign
    // TODO have InMemoryNetworkMapCache and PersistentNetworkMapCache (not a cache...)

    /**
     * Load NetworkMap data from the database if present. Node can start without having NetworkMapService configured.
     */
    private fun loadFromDB() {
        logger.info("Loading node infos from database...")
        val session = sessionFactory?.withOptions()?.
                connection(TransactionManager.current().connection)?.
                openSession()
        session?.use {
            val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
            val result = session.createQuery(criteria).resultList
            if (result.isNotEmpty()) _loadDBSuccess = true
            for (nodeInfo in result) {
                logger.info("Loaded node info: $nodeInfo")
                val publicKey = parsePublicKeyBase58(nodeInfo.legalIdentitiesAndCerts.filter { it.isMain }.single().owningKey)
                val node = nodeInfo.toNodeInfo()
                registeredNodes.put(publicKey, node) // TODO It will change, we won't store it in memory
                changePublisher.onNext(MapChange.Added(node)) // Redeploy bridges after reading from DB on startup.
            }
            if (loadDBSuccess) // Useful only if we don't have NetworkMapService configured so StateMachineManager can start.
                _registrationFuture.set(Unit)
        }
    }

    fun updateInfoDB(nodeInfo: NodeInfo) {
        if(loadNetworkCacheDB) {
            val session = sessionFactory?.withOptions()?.
                    connection(TransactionManager.current().connection)?.
                    flushMode(FlushMode.MANUAL)?.
                    openSession()
            session?.use {
                val nodeInfoEntry = nodeInfo.generateMappedObject(NodeInfoSchemaV1)
                session.merge(nodeInfoEntry)
                session.flush()
            }
        }
    }

    fun removeInfoDB(nodeInfo: NodeInfo) {
        if(loadNetworkCacheDB) {
            val session = sessionFactory?.withOptions()?.
                    connection(TransactionManager.current().connection)?.
                    flushMode(FlushMode.MANUAL)?.
                    openSession()
            session?.use { // TODO remove by linking table!
                val info = session.find(NodeInfoSchemaV1.PersistentNodeInfo::class.java, mapOf("party_name" to nodeInfo.legalIdentity.name)) //find by name
                session.remove(info)
                // TODO with legalIdentityAndCert
                session.flush()
            }
        }
    }

}
