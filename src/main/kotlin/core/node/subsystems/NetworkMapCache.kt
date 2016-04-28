package core.node.subsystems

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import core.Contract
import core.Party
import core.crypto.SecureHash
import core.messaging.MessagingService
import core.messaging.StateMachineManager
import core.messaging.runOnNextMessage
import core.node.NodeInfo
import core.node.services.*
import core.random63BitValue
import core.serialization.deserialize
import core.serialization.serialize
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

    /** A list of nodes that advertise a network map service */
    val networkMapNodes: List<NodeInfo>
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
     * Add a network map service; fetches a copy of the latest map from the service and subscribes to any further
     * updates.
     *
     * @param smm state machine manager to use when requesting
     * @param net the network messaging service
     * @param service the network map service to fetch current state from.
     * @param subscribe if the cache should subscribe to updates
     * @param ifChangedSinceVer an optional version number to limit updating the map based on. If the latest map
     * version is less than or equal to the given version, no update is fetched.
     */
    fun addMapService(smm: StateMachineManager, net: MessagingService, service: NodeInfo,
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
     * @param smm state machine manager to use when requesting
     * @param net the network messaging service
     * @param service the network map service to fetch current state from.
     */
    fun deregisterForUpdates(smm: StateMachineManager, net: MessagingService, service: NodeInfo): ListenableFuture<Unit>
}

/**
 * Extremely simple in-memory cache of the network map.
 */
@ThreadSafe
open class InMemoryNetworkMapCache() : NetworkMapCache {
    override val networkMapNodes: List<NodeInfo>
        get() = get(NetworkMapService.Type)
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

    override fun addMapService(smm: StateMachineManager, net: MessagingService, service: NodeInfo, subscribe: Boolean,
                               ifChangedSinceVer: Int?): ListenableFuture<Unit> {
        if (subscribe && !registeredForPush) {
            // Add handler to the network, for updates received from the remote network map service.
            net.addMessageHandler(NetworkMapService.PUSH_PROTOCOL_TOPIC + ".0", null) { message, r ->
                try {
                    val req = message.data.deserialize<NetworkMapService.Update>()
                    val hash = SecureHash.sha256(req.wireReg.serialize().bits)
                    val ackMessage = net.createMessage(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC + TOPIC_DEFAULT_POSTFIX,
                            NetworkMapService.UpdateAcknowledge(hash, net.myAddress).serialize().bits)
                    net.send(ackMessage, req.replyTo)
                    processUpdatePush(req)
                } catch(e: NodeMapError) {
                    NetworkMapCache.logger.warn("Failure during node map update due to bad update: ${e.javaClass.name}")
                } catch(e: Exception) {
                    NetworkMapCache.logger.error("Exception processing update from network map service", e)
                }
            }
            registeredForPush = true
        }

        // Fetch the network map and register for updates at the same time
        val sessionID = random63BitValue()
        val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVer, net.myAddress, sessionID)

        // Add a message handler for the response, and prepare a future to put the data into.
        // Note that the message handler will run on the network thread (not this one).
        val future = SettableFuture.create<Unit>()
        net.runOnNextMessage(NetworkMapService.FETCH_PROTOCOL_TOPIC + "." + sessionID, MoreExecutors.directExecutor()) { message ->
            val resp = message.data.deserialize<NetworkMapService.FetchMapResponse>()
            // We may not receive any nodes back, if the map hasn't changed since the version specified
            resp.nodes?.forEach { processRegistration(it) }
            future.set(Unit)
        }
        net.send(net.createMessage(NetworkMapService.FETCH_PROTOCOL_TOPIC + ".0", req.serialize().bits), service.address)

        return future
    }

    override fun addNode(node: NodeInfo) {
        registeredNodes[node.identity] = node
    }

    override fun removeNode(node: NodeInfo) {
        registeredNodes.remove(node.identity)
    }

    /**
     * Unsubscribes from updates from the given map service.
     *
     * @param service the network map service to listen to updates from.
     */
    override fun deregisterForUpdates(smm: StateMachineManager, net: MessagingService, service: NodeInfo): ListenableFuture<Unit> {
        // Fetch the network map and register for updates at the same time
        val sessionID = random63BitValue()
        val req = NetworkMapService.SubscribeRequest(false, net.myAddress, sessionID)

        // Add a message handler for the response, and prepare a future to put the data into.
        // Note that the message handler will run on the network thread (not this one).
        val future = SettableFuture.create<Unit>()
        net.runOnNextMessage(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC + "." + sessionID, MoreExecutors.directExecutor()) { message ->
            val resp = message.data.deserialize<NetworkMapService.SubscribeResponse>()
            if (resp.confirmed) {
                future.set(Unit)
            } else {
                future.setException(NetworkCacheError.DeregistrationFailed())
            }
        }
        net.send(net.createMessage(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC + ".0", req.serialize().bits), service.address)

        return future
    }

    fun processUpdatePush(req: NetworkMapService.Update) {
        val reg: NodeRegistration
        try {
            reg = req.wireReg.verified()
        } catch(e: SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
        processRegistration(reg)
    }

    private fun processRegistration(reg: NodeRegistration) {
        // TODO: Implement filtering by sequence number, so we only accept changes that are
        // more recent than the latest change we've processed.
        when (reg.type) {
            AddOrRemove.ADD -> addNode(reg.node)
            AddOrRemove.REMOVE -> removeNode(reg.node)
        }
    }
}

sealed class NetworkCacheError : Exception() {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheError()
}