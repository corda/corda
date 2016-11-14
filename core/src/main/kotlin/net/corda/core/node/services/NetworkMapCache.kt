package net.corda.core.node.services

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Contract
import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.messaging.MessagingService
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. The cache wraps around a map fetched
 * from an authoritative service, and adds easy lookup of the data stored within it. Generally it would be initialised
 * with a specified network map service, which it fetches data from and then subscribes to updates of.
 */
interface NetworkMapCache {
    companion object {
        val logger = LoggerFactory.getLogger(NetworkMapCache::class.java)
    }

    enum class MapChangeType { Added, Removed, Modified }
    data class MapChange(val node: NodeInfo, val prevNodeInfo: NodeInfo?, val type: MapChangeType)

    /** A list of nodes that advertise a network map service */
    val networkMapNodes: List<NodeInfo>
    /** A list of nodes that advertise a notary service */
    val notaryNodes: List<NodeInfo>
    /** A list of all nodes the cache is aware of */
    val partyNodes: List<NodeInfo>
    /** Tracks changes to the network map cache */
    val changed: Observable<MapChange>
    /** Future to track completion of the NetworkMapService registration. */
    val mapServiceRegistered: ListenableFuture<Unit>

    /**
     * A list of nodes that advertise a regulatory service. Identifying the correct regulator for a trade is outside
     * the scope of the network map service, and this is intended solely as a sanity check on configuration stored
     * elsewhere.
     */
    val regulators: List<NodeInfo>

    /**
     * Atomically get the current party nodes and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    fun track(): Pair<List<NodeInfo>, Observable<MapChange>>

    /**
     * Get a copy of all nodes in the map.
     */
    fun get(): Collection<NodeInfo>

    /**
     * Get the collection of nodes which advertise a specific service.
     */
    fun get(serviceType: ServiceType): Collection<NodeInfo>

    /**
     * Get a recommended node that advertises a service, and is suitable for the specified contract and parties.
     * Implementations might understand, for example, the correct regulator to use for specific contracts/parties,
     * or the appropriate oracle for a contract.
     */
    fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo?

    /**
     * Look up the node info for a legal name.
     */
    fun getNodeByLegalName(name: String): NodeInfo?

    /**
     * Look up the node info for a public key tree.
     */
    fun getNodeByPublicKeyTree(publicKeyTree: PublicKeyTree): NodeInfo?

    /**
     * Given a [party], returns a node advertising it as an identity. If more than one node found the result
     * is chosen at random.
     *
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*. If the provided [party] is a group identity, multiple nodes advertising it
     * will be found, and this method will return a randomly chosen one. If [party] is an individual (legal) identity,
     * we currently assume that it will be advertised by one node only, which will be returned as the result.
     */
    fun getRepresentativeNode(party: Party): NodeInfo?

    /**
     * Gets a notary identity by the given name.
     */
    fun getNotary(name: String): Party?

    /**
     * Returns a notary identity advertised by any of the nodes on the network (chosen at random)
     *
     * @param type Limits the result to notaries of the specified type (optional)
     */
    fun getAnyNotary(type: ServiceType? = null): Party?

    /**
     * Checks whether a given party is an advertised notary identity
     */
    fun isNotary(party: Party): Boolean

    /**
     * Add a network map service; fetches a copy of the latest map from the service and subscribes to any further
     * updates.
     *
     * @param net the network messaging service.
     * @param networkMapAddress the network map service to fetch current state from.
     * @param subscribe if the cache should subscribe to updates.
     * @param ifChangedSinceVer an optional version number to limit updating the map based on. If the latest map
     * version is less than or equal to the given version, no update is fetched.
     */
    fun addMapService(net: MessagingService, networkMapAddress: SingleMessageRecipient,
                      subscribe: Boolean, ifChangedSinceVer: Int? = null): ListenableFuture<Unit>

    /**
     * Adds a node to the local cache (generally only used for adding ourselves).
     */
    fun addNode(node: NodeInfo)

    /**
     * Removes a node from the local cache.
     */
    fun removeNode(node: NodeInfo)

    /**
     * Deregister from updates from the given map service.
     *
     * @param net the network messaging service.
     * @param service the network map service to fetch current state from.
     */
    fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit>

    /**
     * For testing where the network map cache is manipulated marks the service as immediately ready.
     */
    @VisibleForTesting
    fun runWithoutMapService()
}

sealed class NetworkCacheError : Exception() {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheError()
}
