package net.corda.node.services.network

import net.corda.core.internal.VisibleForTesting
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.PartyInfo
import net.corda.core.schemas.NodeInfoSchemaV1
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.DEFAULT_SESSION_ID
import net.corda.node.services.api.NetworkCacheError
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.createMessage
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import org.bouncycastle.asn1.x500.X500Name
import org.hibernate.Session
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
@ThreadSafe
open class PersistentNetworkMapCache(private val serviceHub: ServiceHubInternal) : SingletonSerializeAsToken(), NetworkMapCacheInternal {
    companion object {
        val logger = loggerFor<PersistentNetworkMapCache>()
    }

    private var registeredForPush = false
    // TODO Small explanation, partyNodes and registeredNodes is left in memory as it was before, because it will be removed in
    //  next PR that gets rid of services. These maps are used only for queries by service.
    override val partyNodes: List<NodeInfo> get() = registeredNodes.map { it.value }
    override val networkMapNodes: List<NodeInfo> get() = getNodesWithService(NetworkMapService.type)
    private val _changed = PublishSubject.create<MapChange>()
    // We use assignment here so that multiple subscribers share the same wrapped Observable.
    override val changed: Observable<MapChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MapChange> get() = _changed.bufferUntilDatabaseCommit()

    private val _registrationFuture = openFuture<Void?>()
    override val nodeReady: CordaFuture<Void?> get() = _registrationFuture
    protected val registeredNodes: MutableMap<PublicKey, NodeInfo> = Collections.synchronizedMap(HashMap())
    private var _loadDBSuccess: Boolean = false
    override val loadDBSuccess get() = _loadDBSuccess

    init {
        serviceHub.database.transaction { loadFromDB() }
    }

    override fun getPartyInfo(party: Party): PartyInfo? {
        val nodes = serviceHub.database.transaction { queryByIdentityKey(party.owningKey) }
        if (nodes.size == 1 && nodes[0].legalIdentity == party) {
            return PartyInfo.Node(nodes[0])
        }
        for (node in nodes) {
            for (service in node.advertisedServices) {
                if (service.identity.party == party) {
                    return PartyInfo.Service(service)
                }
            }
        }
        return null
    }

    // TODO See comment to queryByLegalName why it's left like that.
    override fun getNodeByLegalName(principal: X500Name): NodeInfo? = partyNodes.singleOrNull { it.legalIdentity.name == principal }
            //serviceHub!!.database.transaction { queryByLegalName(principal).firstOrNull() }
    override fun getNodeByLegalIdentityKey(identityKey: PublicKey): NodeInfo? =
            serviceHub.database.transaction { queryByIdentityKey(identityKey).firstOrNull() }
    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        val wellKnownParty = serviceHub.identityService.partyFromAnonymous(party)
        return wellKnownParty?.let {
            getNodeByLegalIdentityKey(it.owningKey)
        }
    }

    override fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo? = serviceHub.database.transaction { queryByAddress(address) }

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
                serviceHub.database.transaction {
                    updateInfoDB(node)
                    changePublisher.onNext(MapChange.Added(node))
                }
            } else if (previousNode != node) {
                serviceHub.database.transaction {
                    updateInfoDB(node)
                    changePublisher.onNext(MapChange.Modified(node, previousNode))
                }
            }
        }
    }

    override fun removeNode(node: NodeInfo) {
        synchronized(_changed) {
            registeredNodes.remove(node.legalIdentity.owningKey)
            serviceHub.database.transaction {
                removeInfoDB(node)
                changePublisher.onNext(MapChange.Removed(node))
            }
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

    // Changes related to NetworkMap redesign
    // TODO It will be properly merged into network map cache after services removal.

    private inline fun <T> createSession(block: (Session) -> T): T {
        return DatabaseTransactionManager.current().session.let { block(it) }
    }

    private fun getAllInfos(session: Session): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
        return session.createQuery(criteria).resultList
    }

    /**
     * Load NetworkMap data from the database if present. Node can start without having NetworkMapService configured.
     */
    private fun loadFromDB() {
        logger.info("Loading network map from database...")
        createSession {
            val result = getAllInfos(it)
            for (nodeInfo in result) {
                try {
                    logger.info("Loaded node info: $nodeInfo")
                    val publicKey = parsePublicKeyBase58(nodeInfo.legalIdentitiesAndCerts.single { it.isMain }.owningKey)
                    val node = nodeInfo.toNodeInfo()
                    registeredNodes.put(publicKey, node)
                    changePublisher.onNext(MapChange.Added(node)) // Redeploy bridges after reading from DB on startup.
                    _loadDBSuccess = true // This is used in AbstractNode to indicate that node is ready.
                } catch (e: Exception) {
                    logger.warn("Exception parsing network map from the database.", e)
                }
            }
            if (loadDBSuccess) {
                _registrationFuture.set(null) // Useful only if we don't have NetworkMapService configured so StateMachineManager can start.
            }
        }
    }

    private fun updateInfoDB(nodeInfo: NodeInfo) {
        // TODO Temporary workaround to force isolated transaction (otherwise it causes race conditions when processing
        //  network map registration on network map node)
        val session = serviceHub.database.entityManagerFactory.withOptions().connection(serviceHub.database.dataSource.connection
                .apply {
                    autoCommit = false
                    transactionIsolation = 1
                }).openSession()

        val tx = session.beginTransaction()
        // TODO For now the main legal identity is left in NodeInfo, this should be set comparision/come up with index for NodeInfo?
        val info = findByIdentityKey(session, nodeInfo.legalIdentity.owningKey)
        val nodeInfoEntry = generateMappedObject(nodeInfo)
        if (info.isNotEmpty()) {
            nodeInfoEntry.id = info[0].id
        }
        session.merge(nodeInfoEntry)
        tx.commit()
        session.close()
    }

    private fun removeInfoDB(nodeInfo: NodeInfo) {
        createSession {
            val info = findByIdentityKey(it, nodeInfo.legalIdentity.owningKey).single()
            it.remove(info)
        }
    }

    private fun findByIdentityKey(session: Session, identityKey: PublicKey): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        val query = session.createQuery(
                "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.owningKey = :owningKey",
                NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        query.setParameter("owningKey", identityKey.toBase58String())
        return query.resultList
    }

    private fun queryByIdentityKey(identityKey: PublicKey): List<NodeInfo> {
        createSession {
            val result = findByIdentityKey(it, identityKey)
            return result.map { it.toNodeInfo() }
        }
    }

    // TODO It's useless for now, because toString on X500 names is inconsistent and we have:
    //    C=ES,L=Madrid,O=Alice Corp,CN=Alice Corp
    //    CN=Alice Corp,O=Alice Corp,L=Madrid,C=ES
    private fun queryByLegalName(name: X500Name): List<NodeInfo> {
        createSession {
            val query = it.createQuery(
                    "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.name = :name",
                    NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            query.setParameter("name", name.toString())
            val result = query.resultList
            return result.map { it.toNodeInfo() }
        }
    }

    private fun queryByAddress(hostAndPort: NetworkHostAndPort): NodeInfo? {
        createSession {
            val query = it.createQuery(
                    "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.addresses a WHERE a.pk.host = :host AND a.pk.port = :port",
                    NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            query.setParameter("host", hostAndPort.host)
            query.setParameter("port", hostAndPort.port)
            val result = query.resultList
            return if (result.isEmpty()) null
            else result.map { it.toNodeInfo() }.singleOrNull() ?: throw IllegalStateException("More than one node with the same host and port")
        }
    }

    /** Object Relational Mapping support. */
    private fun generateMappedObject(nodeInfo: NodeInfo): NodeInfoSchemaV1.PersistentNodeInfo {
        return NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                addresses = nodeInfo.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.map { NodeInfoSchemaV1.DBPartyAndCertificate(it) }.toSet()
                        // TODO It's workaround to keep the main identity, will be removed in future PR getting rid of services.
                        + NodeInfoSchemaV1.DBPartyAndCertificate(nodeInfo.legalIdentityAndCert, isMain = true),
                platformVersion = nodeInfo.platformVersion,
                advertisedServices = nodeInfo.advertisedServices.map { NodeInfoSchemaV1.DBServiceEntry(it.serialize().bytes) },
                worldMapLocation = nodeInfo.worldMapLocation
        )
    }

    override fun clearNetworkMapCache() {
        serviceHub.database.transaction {
            createSession {
                val result = getAllInfos(it)
                for (nodeInfo in result) it.remove(nodeInfo)
            }
        }
    }
}
