package net.corda.node.services.network

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.api.NetworkMapCacheBaseInternal
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import org.hibernate.Session
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.HashSet

class NetworkMapCacheImpl(
        networkMapCacheBase: NetworkMapCacheBaseInternal,
        private val identityService: IdentityService
) : NetworkMapCacheBaseInternal by networkMapCacheBase, NetworkMapCacheInternal {
    companion object {
        private val logger = loggerFor<NetworkMapCacheImpl>()
    }

    init {
        networkMapCacheBase.allNodes.forEach { it.legalIdentitiesAndCerts.forEach { identityService.verifyAndRegisterIdentity(it) } }
        networkMapCacheBase.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is MapChange.Added) {
                mapChange.node.legalIdentitiesAndCerts.forEach {
                    try {
                        identityService.verifyAndRegisterIdentity(it)
                    } catch (ignore: Exception) {
                        // Log a warning to indicate node info is not added to the network map cache.
                        logger.warn("Node info for :'${it.name}' is not added to the network map due to verification error.")
                    }
                }
            }
        }
    }

    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        val wellKnownParty = identityService.wellKnownPartyFromAnonymous(party)
        return wellKnownParty?.let {
            getNodesByLegalIdentityKey(it.owningKey).firstOrNull()
        }
    }
}

/**
 * Extremely simple in-memory cache of the network map.
 */
@ThreadSafe
open class PersistentNetworkMapCache(
        private val database: CordaPersistence,
        notaries: List<NotaryInfo>
) : SingletonSerializeAsToken(), NetworkMapCacheBaseInternal {
    companion object {
        private val logger = contextLogger()
    }

    private val _changed = PublishSubject.create<MapChange>()
    // We use assignment here so that multiple subscribers share the same wrapped Observable.
    override val changed: Observable<MapChange> = _changed.wrapWithDatabaseTransaction()
    private val changePublisher: rx.Observer<MapChange> get() = _changed.bufferUntilDatabaseCommit()

    // TODO revisit the logic under which nodeReady and loadDBSuccess are set.
    // with the NetworkMapService redesign their meaning is not too well defined.
    private val _registrationFuture = openFuture<Void?>()
    override val nodeReady: CordaFuture<Void?> get() = _registrationFuture
    private var _loadDBSuccess: Boolean = false
    override val loadDBSuccess get() = _loadDBSuccess

    fun start(): PersistentNetworkMapCache {
        // if we find any network map information in the db, we are good to go - if not
        // we have to wait for some being added
        synchronized(_changed) {
            val allNodes = database.transaction { getAllInfos(session) }
            if (allNodes.isNotEmpty()) {
                _loadDBSuccess = true
            }
            allNodes.forEach {
                changePublisher.onNext(MapChange.Added(it.toNodeInfo()))
            }
            _registrationFuture.set(null)
        }
        return this
    }

    override val notaryIdentities: List<Party> = notaries.map { it.identity }
    private val validatingNotaries = notaries.mapNotNullTo(HashSet()) { if (it.validating) it.identity else null }

    override val allNodeHashes: List<SecureHash>
        get() {
            return database.transaction {
                val builder = session.criteriaBuilder
                val query = builder.createQuery(String::class.java).run {
                    from(NodeInfoSchemaV1.PersistentNodeInfo::class.java).run {
                        select(get<String>(NodeInfoSchemaV1.PersistentNodeInfo::hash.name))
                    }
                }
                session.createQuery(query).resultList.map { SecureHash.parse(it) }
            }
        }

    override fun getNodeByHash(nodeHash: SecureHash): NodeInfo? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java).run {
                from(NodeInfoSchemaV1.PersistentNodeInfo::class.java).run {
                    where(builder.equal(get<String>(NodeInfoSchemaV1.PersistentNodeInfo::hash.name), nodeHash.toString()))
                }
            }
            session.createQuery(query).resultList.singleOrNull()?.toNodeInfo()
        }
    }

    override fun isValidatingNotary(party: Party): Boolean = party in validatingNotaries

    override fun getPartyInfo(party: Party): PartyInfo? {
        val nodes = getNodesByLegalIdentityKey(party.owningKey)
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

    override fun getNodeByLegalName(name: CordaX500Name): NodeInfo? {
        val nodeInfos = getNodesByLegalName(name)
        return when (nodeInfos.size) {
            0 -> null
            1 -> nodeInfos[0]
            else -> throw IllegalArgumentException("More than one node found with legal name $name")
        }
    }

    override fun getNodesByLegalName(name: CordaX500Name): List<NodeInfo> = database.transaction { queryByLegalName(session, name) }

    override fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo> = nodesByKeyCache[identityKey]

    private val nodesByKeyCache = NonInvalidatingCache<PublicKey, List<NodeInfo>>(1024, 8, { key -> database.transaction { queryByIdentityKey(session, key) } })

    override fun getNodesByOwningKeyIndex(identityKeyIndex: String): List<NodeInfo> {
        return database.transaction {
            queryByIdentityKeyIndex(session, identityKeyIndex)
        }
    }

    override fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo? = database.transaction { queryByAddress(session, address) }

    override fun getPeerCertificateByLegalName(name: CordaX500Name): PartyAndCertificate? = identityByLegalNameCache.get(name).orElse(null)

    private val identityByLegalNameCache = NonInvalidatingCache<CordaX500Name, Optional<PartyAndCertificate>>(1024, 8, { name -> Optional.ofNullable(database.transaction { queryIdentityByLegalName(session, name) }) })

    override fun track(): DataFeed<List<NodeInfo>, MapChange> {
        synchronized(_changed) {
            val allInfos = database.transaction { getAllInfos(session) }.map { it.toNodeInfo() }
            return DataFeed(allInfos, _changed.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    override fun addNode(node: NodeInfo) {
        logger.info("Adding node with info: $node")
        synchronized(_changed) {
            val previousNode = getNodesByLegalIdentityKey(node.legalIdentities.first().owningKey).firstOrNull()
            if (previousNode == null) {
                logger.info("No previous node found")
                database.transaction {
                    updateInfoDB(node, session)
                    changePublisher.onNext(MapChange.Added(node))
                }
            } else if (previousNode.serial > node.serial) {
                logger.info("Discarding older nodeInfo for ${node.legalIdentities.first().name}")
                return
            } else if (previousNode != node) {
                logger.info("Previous node was found as: $previousNode")
                database.transaction {
                    updateInfoDB(node, session)
                    changePublisher.onNext(MapChange.Modified(node, previousNode))
                }
            } else {
                logger.info("Previous node was identical to incoming one - doing nothing")
            }
        }
        _loadDBSuccess = true // This is used in AbstractNode to indicate that node is ready.
        _registrationFuture.set(null)
        logger.info("Done adding node with info: $node")
    }

    override fun removeNode(node: NodeInfo) {
        logger.info("Removing node with info: $node")
        synchronized(_changed) {
            database.transaction {
                removeInfoDB(session, node)
                changePublisher.onNext(MapChange.Removed(node))
            }
        }
        logger.info("Done removing node with info: $node")
    }

    override val allNodes: List<NodeInfo>
        get() = database.transaction {
            getAllInfos(session).map { it.toNodeInfo() }
        }

    private fun getAllInfos(session: Session): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
        return session.createQuery(criteria).resultList
    }

    private fun updateInfoDB(nodeInfo: NodeInfo, session: Session) {
        // TODO For now the main legal identity is left in NodeInfo, this should be set comparision/come up with index for NodeInfo?
        val info = findByIdentityKey(session, nodeInfo.legalIdentitiesAndCerts.first().owningKey)
        val nodeInfoEntry = generateMappedObject(nodeInfo)
        if (info.isNotEmpty()) {
            nodeInfoEntry.id = info.first().id
        }
        session.merge(nodeInfoEntry)
        // invalidate cache last - this way, we might serve up the wrong info for a short time, but it will get refreshed
        // on the next load
        invalidateCaches(nodeInfo)
    }

    private fun removeInfoDB(session: Session, nodeInfo: NodeInfo) {
        val info = findByIdentityKey(session, nodeInfo.legalIdentitiesAndCerts.first().owningKey).singleOrNull()
        info?.let { session.remove(it) }
        // invalidate cache last - this way, we might serve up the wrong info for a short time, but it will get refreshed
        // on the next load
        invalidateCaches(nodeInfo)
    }

    private fun findByIdentityKey(session: Session, identityKey: PublicKey): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        return findByIdentityKeyIndex(session, identityKey.toStringShort())
    }

    private fun findByIdentityKeyIndex(session: Session, identityKeyIndex: String): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        val query = session.createQuery(
                "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.owningKeyHash = :owningKeyHash",
                NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        query.setParameter("owningKeyHash", identityKeyIndex)
        return query.resultList
    }

    private fun queryByIdentityKey(session: Session, identityKey: PublicKey): List<NodeInfo> {
        return queryByIdentityKeyIndex(session, identityKey.toStringShort())
    }

    private fun queryByIdentityKeyIndex(session: Session, identityKeyIndex: String): List<NodeInfo> {
        val result = findByIdentityKeyIndex(session, identityKeyIndex)
        return result.map { it.toNodeInfo() }
    }

    private fun queryIdentityByLegalName(session: Session, name: CordaX500Name): PartyAndCertificate? {
        val query = session.createQuery(
                // We do the JOIN here to restrict results to those present in the network map
                "SELECT DISTINCT l FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.name = :name",
                NodeInfoSchemaV1.DBPartyAndCertificate::class.java)
        query.setParameter("name", name.toString())
        val candidates = query.resultList.map { it.toLegalIdentityAndCert() }
        // The map is restricted to holding a single identity for any X.500 name, so firstOrNull() is correct here.
        return candidates.firstOrNull()
    }

    private fun queryByLegalName(session: Session, name: CordaX500Name): List<NodeInfo> {
        val query = session.createQuery(
                "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.legalIdentitiesAndCerts l WHERE l.name = :name",
                NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        query.setParameter("name", name.toString())
        val result = query.resultList
        return result.map { it.toNodeInfo() }
    }

    private fun queryByAddress(session: Session, hostAndPort: NetworkHostAndPort): NodeInfo? {
        val query = session.createQuery(
                "SELECT n FROM ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name} n JOIN n.addresses a WHERE a.host = :host AND a.port = :port",
                NodeInfoSchemaV1.PersistentNodeInfo::class.java)
        query.setParameter("host", hostAndPort.host)
        query.setParameter("port", hostAndPort.port)
        query.setMaxResults(1)
        val result = query.resultList
        return result.map { it.toNodeInfo() }.singleOrNull()
    }

    /** Object Relational Mapping support. */
    private fun generateMappedObject(nodeInfo: NodeInfo): NodeInfoSchemaV1.PersistentNodeInfo {
        return NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                hash = nodeInfo.serialize().hash.toString(),
                addresses = nodeInfo.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo.platformVersion,
                serial = nodeInfo.serial
        )
    }

    /** We are caching data we get from the db - if we modify the db, they need to be cleared out*/
    private fun invalidateCaches(nodeInfo: NodeInfo) {
        nodesByKeyCache.invalidateAll(nodeInfo.legalIdentities.map { it.owningKey })
        identityByLegalNameCache.invalidateAll(nodeInfo.legalIdentities.map { it.name })
    }

    private fun invalidateCaches() {
        nodesByKeyCache.invalidateAll()
        identityByLegalNameCache.invalidateAll()
    }

    override fun clearNetworkMapCache() {
        invalidateCaches()
        database.transaction {
            val result = getAllInfos(session)
            for (nodeInfo in result) session.remove(nodeInfo)
        }
    }
}
