package com.r3corda.core.node.services

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.Contract
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.NodeInfo
import org.slf4j.LoggerFactory
import java.security.PublicKey

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

    /** A list of nodes that advertise a network map service */
    val networkMapNodes: List<NodeInfo>
    /** A list of nodes that advertise a notary service */
    val notaryNodes: List<NodeInfo>
    /** A list of nodes that advertise a rates oracle service */
    val ratesOracleNodes: List<NodeInfo>
    /** A list of all nodes the cache is aware of */
    val partyNodes: List<NodeInfo>
    /**
     * A list of nodes that advertise a regulatory service. Identifying the correct regulator for a trade is outside
     * the scope of the network map service, and this is intended solely as a sanity check on configuration stored
     * elsewhere.
     */
    val regulators: List<NodeInfo>

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
     * Look up the node info for a public key.
     */
    fun getNodeByPublicKey(publicKey: PublicKey): NodeInfo?

    /**
     * Add a network map service; fetches a copy of the latest map from the service and subscribes to any further
     * updates.
     *
     * @param net the network messaging service
     * @param service the network map service to fetch current state from.
     * @param subscribe if the cache should subscribe to updates
     * @param ifChangedSinceVer an optional version number to limit updating the map based on. If the latest map
     * version is less than or equal to the given version, no update is fetched.
     */
    fun addMapService(net: MessagingService, service: NodeInfo,
                      subscribe: Boolean, ifChangedSinceVer: Int? = null): ListenableFuture<Unit>

    /**
     * Adds a node to the local cache (generally only used for adding ourselves)
     */
    fun addNode(node: NodeInfo)

    /**
     * Removes a node from the local cache
     */
    fun removeNode(node: NodeInfo)

    /**
     * Deregister from updates from the given map service.
     *
     * @param net the network messaging service
     * @param service the network map service to fetch current state from.
     */
    fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit>
}

sealed class NetworkCacheError : Exception() {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheError()
}