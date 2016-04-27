package core.node.services

import core.Contract
import core.Party
import core.node.NodeInfo
import core.utilities.AddOrRemove
import org.slf4j.LoggerFactory
import java.security.SignatureException
import java.util.*
import javax.annotation.concurrent.ThreadSafe

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

    /** A list of nodes that advertise a timestamping service */
    val timestampingNodes: List<NodeInfo>
    /** A list of nodes that advertise a rates oracle service */
    val ratesOracleNodes: List<NodeInfo>
    /** A list of all nodes the cache is aware of */
    val partyNodes: List<NodeInfo>
    /** A list of nodes that advertise a regulatory service. Identifying the correct regulator for a trade is outwith
     * the scope of the network map service, and this is intended solely as a sanity check on configuration stored
     * elsewhere.
     */
    val regulators: List<NodeInfo>

    /**
     * Look up the node info for a party.
     */
    fun nodeForPartyName(name: String): NodeInfo? = partyNodes.singleOrNull { it.identity.name == name }

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
     * Adds a node to the local cache (generally only used for adding ourselves)
     */
    fun addNode(node: NodeInfo)

    /**
     * Removes a node from the local cache
     */
    fun removeNode(node: NodeInfo)
}

/**
 * Extremely simple in-memory cache of the network map.
 */
@ThreadSafe
open class InMemoryNetworkMapCache() : NetworkMapCache {
    override val regulators: List<NodeInfo>
        get() = get(RegulatorService.Type)
    override val timestampingNodes: List<NodeInfo>
        get() = get(TimestamperService.Type)
    override val ratesOracleNodes: List<NodeInfo>
        get() = get(NodeInterestRates.Type)
    override val partyNodes: List<NodeInfo>
        get() = registeredNodes.map { it.value }

    private var registeredForPush = false
    protected var registeredNodes = Collections.synchronizedMap(HashMap<Party, NodeInfo>())

    override fun get() = registeredNodes.map { it.value }
    override fun get(serviceType: ServiceType) = registeredNodes.filterValues { it.advertisedServices.contains(serviceType) }.map { it.value }
    override fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo? = get(type).firstOrNull()

    override fun addNode(node: NodeInfo) {
        registeredNodes[node.identity] = node
    }

    override fun removeNode(node: NodeInfo) {
        registeredNodes.remove(node.identity)
    }
}

sealed class NetworkCacheError : Exception() {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheError()
}