package net.corda.node.services.network

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.PartyInfo
import net.corda.core.schemas.NodeInfoSchemaV1
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toBase58String
import net.corda.node.services.api.NetworkCacheException
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.createMessage
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.NetworkMapService.FetchMapResponse
import net.corda.node.services.network.NetworkMapService.SubscribeResponse
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.DatabaseTransactionManager
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.wrapWithDatabaseTransaction
import org.hibernate.Session
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.HashMap

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
    protected val registeredNodes: MutableMap<PublicKey, NodeInfo> = Collections.synchronizedMap(HashMap())
    protected val partyNodes: MutableList<NodeInfo> get() = registeredNodes.map { it.value }.toMutableList()
    private val _changed = PublishSubject.create<MapChange>()
    // We use assignment here so that multiple subscribers share the same wrapped Observable.
    override val changed: Observable<MapChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MapChange> get() = _changed.bufferUntilDatabaseCommit()

    private val _registrationFuture = openFuture<Void?>()
    override val nodeReady: CordaFuture<Void?> get() = _registrationFuture
    private var _loadDBSuccess: Boolean = false
    override val loadDBSuccess get() = _loadDBSuccess
    // TODO From the NetworkMapService redesign doc: Remove the concept of network services.
    //  As a temporary hack, just assume for now that every network has a notary service named "Notary Service" that can be looked up in the map.
    //  This should eliminate the only required usage of services.
    //  It is ensured on node startup when constructing a notary that the name contains "notary".
    override val notaryIdentities: List<Party>
        get() {
            return partyNodes
                    .flatMap {
                        // TODO: validate notary identity certificates before loading into network map cache.
                        //       Notary certificates have to be signed by the doorman directly
                        it.legalIdentities
                    }
                    .filter { it.name.commonName?.startsWith(NotaryService.ID_PREFIX) ?: false }
                    .toSet() // Distinct, because of distributed service nodes
                    .sortedBy { it.name.toString() }
        }

    private val nodeInfoSerializer = NodeInfoWatcher(serviceHub.configuration.baseDirectory)

    init {
        loadFromFiles()
        serviceHub.database.transaction { loadFromDB() }
    }

    private fun loadFromFiles() {
        logger.info("Loading network map from files..")
        nodeInfoSerializer.nodeInfoUpdates().subscribe { node -> addNode(node) }
    }

    override fun getPartyInfo(party: Party): PartyInfo? {
        val nodes = serviceHub.database.transaction { queryByIdentityKey(party.owningKey) }
        if (nodes.size == 1 && nodes[0].isLegalIdentity(party)) {
            return PartyInfo.SingleNode(party, nodes[0].addresses)
        }
        for (node in nodes) {
            for (identity in node.legalIdentities) {
                if (identity == party) {
                    return PartyInfo.DistributedNode(party)
                }
            }
        }
        return null
    }

    override fun getNodeByLegalName(name: CordaX500Name): NodeInfo? = getNodesByLegalName(name).firstOrNull()
    override fun getNodesByLegalName(name: CordaX500Name): List<NodeInfo> = serviceHub.database.transaction { queryByLegalName(name) }
    override fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo> =
            serviceHub.database.transaction { queryByIdentityKey(identityKey) }

    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        val wellKnownParty = serviceHub.identityService.wellKnownPartyFromAnonymous(party)
        return wellKnownParty?.let {
            getNodesByLegalIdentityKey(it.owningKey).firstOrNull()
        }
    }

    override fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo? = serviceHub.database.transaction { queryByAddress(address) }

    override fun getPeerCertificateByLegalName(name: CordaX500Name): PartyAndCertificate? = serviceHub.database.transaction { queryIdentityByLegalName(name) }

    override fun track(): DataFeed<List<NodeInfo>, MapChange> {
        synchronized(_changed) {
            return DataFeed(partyNodes, _changed.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun addMapService(network: MessagingService, networkMapAddress: SingleMessageRecipient, subscribe: Boolean,
                               ifChangedSinceVer: Int?): CordaFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            network.addMessageHandler(NetworkMapService.PUSH_TOPIC) { message, _ ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val ackMessage = network.createMessage(NetworkMapService.PUSH_ACK_TOPIC,
                            data = NetworkMapService.UpdateAcknowledge(req.mapVersion, network.myAddress).serialize().bytes)
                    network.send(ackMessage, req.replyTo)
                    processUpdatePush(req)
                } catch (e: NodeMapException) {
                    logger.warn("Failure during node map update due to bad update: ${e.javaClass.name}")
                } catch (e: Exception) {
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
        logger.info("Adding node with info: $node")
        synchronized(_changed) {
            registeredNodes[node.legalIdentities.first().owningKey]?.let {
                if (it.serial > node.serial) {
                    logger.info("Discarding older nodeInfo for ${node.legalIdentities.first().name}")
                    return
                }
            }
            val previousNode = registeredNodes.put(node.legalIdentities.first().owningKey, node) // TODO hack... we left the first one as special one
            if (previousNode == null) {
                logger.info("No previous node found")
                serviceHub.database.transaction {
                    updateInfoDB(node)
                    changePublisher.onNext(MapChange.Added(node))
                }
            } else if (previousNode != node) {
                logger.info("Previous node was found as: $previousNode")
                serviceHub.database.transaction {
                    updateInfoDB(node)
                    changePublisher.onNext(MapChange.Modified(node, previousNode))
                }
            } else {
                logger.info("Previous node was identical to incoming one - doing nothing")
            }
        }
        logger.info("Done adding node with info: $node")
    }

    override fun removeNode(node: NodeInfo) {
        logger.info("Removing node with info: $node")
        synchronized(_changed) {
            registeredNodes.remove(node.legalIdentities.first().owningKey)
            serviceHub.database.transaction {
                removeInfoDB(node)
                changePublisher.onNext(MapChange.Removed(node))
            }
        }
        logger.info("Done removing node with info: $node")
    }

    /**
     * Unsubscribes from updates from the given map service.
     * @param mapParty the network map service party to listen to updates from.
     */
    override fun deregisterForUpdates(network: MessagingService, mapParty: Party): CordaFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val req = NetworkMapService.SubscribeRequest(false, network.myAddress)
        // `network.getAddressOfParty(partyInfo)` is a work-around for MockNetwork and InMemoryMessaging to get rid of SingleMessageRecipient in NodeInfo.
        val address = getPartyInfo(mapParty)?.let { network.getAddressOfParty(it) } ?:
                throw IllegalArgumentException("Can't deregister for updates, don't know the party: $mapParty")
        val future = network.sendRequest<SubscribeResponse>(NetworkMapService.SUBSCRIPTION_TOPIC, req, address).map {
            if (it.confirmed) Unit else throw NetworkCacheException.DeregistrationFailed()
        }
        _registrationFuture.captureLater(future.map { null })
        return future
    }

    fun processUpdatePush(req: NetworkMapService.Update) {
        try {
            val reg = req.wireReg.verified()
            processRegistration(reg)
        } catch (e: SignatureException) {
            throw NodeMapException.InvalidSignature()
        }
    }

    override val allNodes: List<NodeInfo>
        get () = serviceHub.database.transaction {
            createSession {
                getAllInfos(it).map { it.toNodeInfo() }
            }
        }

    private fun processRegistration(reg: NodeRegistration) {
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
                    val node = nodeInfo.toNodeInfo()
                    addNode(node)
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
        serviceHub.database.dataSource.connection.use {
            val session = serviceHub.database.entityManagerFactory.withOptions().connection(it.apply {
                transactionIsolation = 1
            }).openSession()
            session.use {
                val tx = session.beginTransaction()
                // TODO For now the main legal identity is left in NodeInfo, this should be set comparision/come up with index for NodeInfo?
                val info = findByIdentityKey(session, nodeInfo.legalIdentitiesAndCerts.first().owningKey)
                val nodeInfoEntry = generateMappedObject(nodeInfo)
                if (info.isNotEmpty()) {
                    nodeInfoEntry.id = info[0].id
                }
                session.merge(nodeInfoEntry)
                tx.commit()
            }
        }
    }

    private fun removeInfoDB(nodeInfo: NodeInfo) {
        createSession {
            val info = findByIdentityKey(it, nodeInfo.legalIdentitiesAndCerts.first().owningKey).single()
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

    private fun queryIdentityByLegalName(name: CordaX500Name): PartyAndCertificate? {
        createSession {
            val query = it.createQuery(
                    // We do the JOIN here to restrict results to those present in the network map
                    "SELECT DISTINCT l FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.name = :name",
                    NodeInfoSchemaV1.DBPartyAndCertificate::class.java)
            query.setParameter("name", name.toString())
            val candidates = query.resultList.map { it.toLegalIdentityAndCert() }
            // The map is restricted to holding a single identity for any X.500 name, so firstOrNull() is correct here.
            return candidates.firstOrNull()
        }
    }

    private fun queryByLegalName(name: CordaX500Name): List<NodeInfo> {
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
                // TODO Another ugly hack with special first identity...
                legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo.platformVersion,
                serial = nodeInfo.serial
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
