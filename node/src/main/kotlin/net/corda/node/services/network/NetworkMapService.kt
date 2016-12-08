package net.corda.node.services.network

import com.google.common.annotations.VisibleForTesting
import kotlinx.support.jdk8.collections.compute
import net.corda.core.ThreadBox
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.signWithECDSA
import net.corda.core.messaging.MessageHandlerRegistration
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.createMessage
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.ServiceType
import net.corda.core.random63BitValue
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.flows.ServiceRequestMessage
import net.corda.node.services.api.AbstractNodeService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.AddOrRemove
import java.security.PrivateKey
import java.security.SignatureException
import java.time.Instant
import java.time.Period
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.concurrent.ThreadSafe

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. This information is cached locally within
 * nodes, by the [NetworkMapCache]. Currently very basic consensus controls are applied, using signed changes which
 * replace each other based on a serial number present in the change.
 */
// TODO: A better architecture for the network map service might be one like the Tor directory authorities, where
// several nodes linked by RAFT or Paxos elect a leader and that leader distributes signed documents describing the
// network layout. Those documents can then be cached by every node and thus a network map can/ be retrieved given only
// a single successful peer connection.
//
// It may also be that this is replaced or merged with the identity management service; for example if the network has
// a concept of identity changes over time, should that include the node for an identity? If so, that is likely to
// replace this service.
interface NetworkMapService {

    companion object {
        val DEFAULT_EXPIRATION_PERIOD = Period.ofWeeks(4)
        val FETCH_FLOW_TOPIC = "platform.network_map.fetch"
        val QUERY_FLOW_TOPIC = "platform.network_map.query"
        val REGISTER_FLOW_TOPIC = "platform.network_map.register"
        val SUBSCRIPTION_FLOW_TOPIC = "platform.network_map.subscribe"
        // Base topic used when pushing out updates to the network map. Consumed, for example, by the map cache.
        // When subscribing to these updates, remember they must be acknowledged
        val PUSH_FLOW_TOPIC = "platform.network_map.push"
        // Base topic for messages acknowledging pushed updates
        val PUSH_ACK_FLOW_TOPIC = "platform.network_map.push_ack"

        val logger = loggerFor<NetworkMapService>()

        val type = ServiceType.corda.getSubType("network_map")
    }

    val nodes: List<NodeInfo>

    class FetchMapRequest(val subscribe: Boolean,
                          val ifChangedSinceVersion: Int?,
                          override val replyTo: SingleMessageRecipient,
                          override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    data class FetchMapResponse(val nodes: Collection<NodeRegistration>?, val version: Int)

    class QueryIdentityRequest(val identity: Party,
                               override val replyTo: SingleMessageRecipient,
                               override val sessionID: Long) : ServiceRequestMessage

    data class QueryIdentityResponse(val node: NodeInfo?)

    class RegistrationRequest(val wireReg: WireNodeRegistration,
                              override val replyTo: SingleMessageRecipient,
                              override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    data class RegistrationResponse(val success: Boolean)

    class SubscribeRequest(val subscribe: Boolean,
                           override val replyTo: SingleMessageRecipient,
                           override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    data class SubscribeResponse(val confirmed: Boolean)

    data class Update(val wireReg: WireNodeRegistration, val mapVersion: Int, val replyTo: MessageRecipients)
    data class UpdateAcknowledge(val mapVersion: Int, val replyTo: MessageRecipients)
}

@ThreadSafe
class InMemoryNetworkMapService(services: ServiceHubInternal) : AbstractNetworkMapService(services) {

    override val registeredNodes: MutableMap<Party, NodeRegistrationInfo> = ConcurrentHashMap()
    override val subscribers = ThreadBox(mutableMapOf<SingleMessageRecipient, LastAcknowledgeInfo>())

    init {
        setup()
    }
}

/**
 * Abstracted out core functionality as the basis for a persistent implementation, as well as existing in-memory implementation.
 *
 * Design is slightly refactored to track time and map version of last acknowledge per subscriber to facilitate
 * subscriber clean up and is simpler to persist than the previous implementation based on a set of missing messages acks.
 */
@ThreadSafe
abstract class AbstractNetworkMapService
(services: ServiceHubInternal) : NetworkMapService, AbstractNodeService(services) {
    protected abstract val registeredNodes: MutableMap<Party, NodeRegistrationInfo>

    // Map from subscriber address, to most recently acknowledged update map version.
    protected abstract val subscribers: ThreadBox<MutableMap<SingleMessageRecipient, LastAcknowledgeInfo>>

    protected val _mapVersion = AtomicInteger(0)

    @VisibleForTesting
    val mapVersion: Int
        get() = _mapVersion.get()

    private fun mapVersionIncrementAndGet(): Int = _mapVersion.incrementAndGet()

    /** Maximum number of unacknowledged updates to send to a node before automatically unregistering them for updates */
    val maxUnacknowledgedUpdates = 10
    /**
     * Maximum credible size for a registration request. Generally requests are around 500-600 bytes, so this gives a
     * 10 times overhead.
     */
    val maxSizeRegistrationRequestBytes = 5500

    private val handlers = ArrayList<MessageHandlerRegistration>()

    // Filter reduces this to the entries that add a node to the map
    override val nodes: List<NodeInfo>
        get() = registeredNodes.mapNotNull { if (it.value.reg.type == AddOrRemove.ADD) it.value.reg.node else null }

    protected fun setup() {
        // Register message handlers
        handlers += addMessageHandler(NetworkMapService.FETCH_FLOW_TOPIC,
                { req: NetworkMapService.FetchMapRequest -> processFetchAllRequest(req) }
        )
        handlers += addMessageHandler(NetworkMapService.QUERY_FLOW_TOPIC,
                { req: NetworkMapService.QueryIdentityRequest -> processQueryRequest(req) }
        )
        handlers += addMessageHandler(NetworkMapService.REGISTER_FLOW_TOPIC,
                { req: NetworkMapService.RegistrationRequest -> processRegistrationChangeRequest(req) }
        )
        handlers += addMessageHandler(NetworkMapService.SUBSCRIPTION_FLOW_TOPIC,
                { req: NetworkMapService.SubscribeRequest -> processSubscriptionRequest(req) }
        )
        handlers += net.addMessageHandler(NetworkMapService.PUSH_ACK_FLOW_TOPIC, DEFAULT_SESSION_ID) { message, r ->
            val req = message.data.deserialize<NetworkMapService.UpdateAcknowledge>()
            processAcknowledge(req)
        }
    }

    @VisibleForTesting
    fun unregisterNetworkHandlers() {
        for (handler in handlers) {
            net.removeMessageHandler(handler)
        }
        handlers.clear()
    }

    private fun addSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapError.InvalidSubscriber()
        subscribers.locked {
            if (!containsKey(subscriber)) {
                put(subscriber, LastAcknowledgeInfo(mapVersion))
            }
        }
    }

    private fun removeSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapError.InvalidSubscriber()
        subscribers.locked { remove(subscriber) }
    }

    @VisibleForTesting
    fun getUnacknowledgedCount(subscriber: SingleMessageRecipient, mapVersion: Int): Int? {
        return subscribers.locked {
            val subscriberMapVersion = get(subscriber)?.mapVersion
            if (subscriberMapVersion != null) {
                mapVersion - subscriberMapVersion
            } else {
                null
            }
        }
    }

    @VisibleForTesting
    fun notifySubscribers(wireReg: WireNodeRegistration, mapVersion: Int) {
        // TODO: Once we have a better established messaging system, we can probably send
        //       to a MessageRecipientGroup that nodes join/leave, rather than the network map
        //       service itself managing the group
        val update = NetworkMapService.Update(wireReg, mapVersion, net.myAddress).serialize().bytes
        val message = net.createMessage(NetworkMapService.PUSH_FLOW_TOPIC, DEFAULT_SESSION_ID, update)

        subscribers.locked {
            val toRemove = mutableListOf<SingleMessageRecipient>()
            forEach { subscriber: Map.Entry<SingleMessageRecipient, LastAcknowledgeInfo> ->
                val unacknowledgedCount = mapVersion - subscriber.value.mapVersion
                // TODO: introduce some concept of time in the condition to avoid unsubscribes when there's a message burst.
                if (unacknowledgedCount <= maxUnacknowledgedUpdates) {
                    net.send(message, subscriber.key)
                } else {
                    toRemove.add(subscriber.key)
                }
            }
            toRemove.forEach { remove(it) }
        }
    }

    @VisibleForTesting
    fun processAcknowledge(req: NetworkMapService.UpdateAcknowledge): Unit {
        if (req.replyTo !is SingleMessageRecipient) throw NodeMapError.InvalidSubscriber()
        subscribers.locked {
            val lastVersionAcked = this[req.replyTo]?.mapVersion
            if ((lastVersionAcked ?: 0) < req.mapVersion) {
                this[req.replyTo] = LastAcknowledgeInfo(req.mapVersion)
            }
        }
    }

    @VisibleForTesting
    fun processFetchAllRequest(req: NetworkMapService.FetchMapRequest): NetworkMapService.FetchMapResponse {
        if (req.subscribe) {
            addSubscriber(req.replyTo)
        }
        val ver = mapVersion
        if (req.ifChangedSinceVersion == null || req.ifChangedSinceVersion < ver) {
            val nodes = ArrayList(registeredNodes.values.map { it.reg })  // Snapshot to avoid attempting to serialise Map internals
            return NetworkMapService.FetchMapResponse(nodes, ver)
        } else {
            return NetworkMapService.FetchMapResponse(null, ver)
        }
    }

    @VisibleForTesting
    fun processQueryRequest(req: NetworkMapService.QueryIdentityRequest): NetworkMapService.QueryIdentityResponse {
        val candidate = registeredNodes[req.identity]?.reg

        // If the most recent record we have is of the node being removed from the map, then it's considered
        // as no match.
        if (candidate == null || candidate.type == AddOrRemove.REMOVE) {
            return NetworkMapService.QueryIdentityResponse(null)
        } else {
            return NetworkMapService.QueryIdentityResponse(candidate.node)
        }
    }

    @VisibleForTesting
    fun processRegistrationChangeRequest(req: NetworkMapService.RegistrationRequest): NetworkMapService.RegistrationResponse {
        require(req.wireReg.raw.size < maxSizeRegistrationRequestBytes)
        val change: NodeRegistration

        try {
            change = req.wireReg.verified()
        } catch(e: SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
        val node = change.node

        var changed: Boolean = false
        // Update the current value atomically, so that if multiple updates come
        // in on different threads, there is no risk of a race condition while checking
        // sequence numbers.
        val registrationInfo = registeredNodes.compute(node.legalIdentity, { mapKey: Party, existing: NodeRegistrationInfo? ->
            changed = existing == null || existing.reg.serial < change.serial
            if (changed) {
                when (change.type) {
                    AddOrRemove.ADD -> NodeRegistrationInfo(change, mapVersionIncrementAndGet())
                    AddOrRemove.REMOVE -> NodeRegistrationInfo(change, mapVersionIncrementAndGet())
                    else -> throw NodeMapError.UnknownChangeType()
                }
            } else {
                existing
            }
        })
        if (changed) {
            notifySubscribers(req.wireReg, registrationInfo!!.mapVersion)

            // Update the local cache
            // TODO: Once local messaging is fixed, this should go over the network layer as it does to other
            // subscribers
            when (change.type) {
                AddOrRemove.ADD -> {
                    NetworkMapService.logger.info("Added node ${node.address} to network map")
                    services.networkMapCache.addNode(change.node)
                }
                AddOrRemove.REMOVE -> {
                    NetworkMapService.logger.info("Removed node ${node.address} from network map")
                    services.networkMapCache.removeNode(change.node)
                }
            }

        }
        return NetworkMapService.RegistrationResponse(changed)
    }

    @VisibleForTesting
    fun processSubscriptionRequest(req: NetworkMapService.SubscribeRequest): NetworkMapService.SubscribeResponse {
        when (req.subscribe) {
            false -> removeSubscriber(req.replyTo)
            true -> addSubscriber(req.replyTo)
        }
        return NetworkMapService.SubscribeResponse(true)
    }
}

/**
 * A node registration state in the network map.
 *
 * @param node the node being added/removed.
 * @param serial an increasing value which represents the version of this registration. Not expected to be sequential,
 * but later versions of the registration must have higher values (or they will be ignored by the map service).
 * Similar to the serial number on DNS records.
 * @param type add if the node is being added to the map, or remove if a previous node is being removed (indicated as
 * going offline).
 * @param expires when the registration expires. Only used when adding a node to a map.
 */
// TODO: This might alternatively want to have a node and party, with the node being optional, so registering a node
// involves providing both node and paerty, and deregistering a node involves a request with party but no node.
class NodeRegistration(val node: NodeInfo, val serial: Long, val type: AddOrRemove, var expires: Instant) {
    /**
     * Build a node registration in wire format.
     */
    fun toWire(privateKey: PrivateKey): WireNodeRegistration {
        val regSerialized = this.serialize()
        val regSig = privateKey.signWithECDSA(regSerialized.bytes, node.legalIdentity.owningKey.singleKey)

        return WireNodeRegistration(regSerialized, regSig)
    }

    override fun toString(): String = "$node #$serial ($type)"
}

/**
 * A node registration and its signature as a pair.
 */
class WireNodeRegistration(raw: SerializedBytes<NodeRegistration>, sig: DigitalSignature.WithKey) : SignedData<NodeRegistration>(raw, sig) {
    @Throws(IllegalArgumentException::class)
    override fun verifyData(data: NodeRegistration) {
        require(data.node.legalIdentity.owningKey.isFulfilledBy(sig.by))
    }
}

sealed class NodeMapError : Exception() {

    /** Thrown if the signature on the node info does not match the public key for the identity */
    class InvalidSignature : NodeMapError()

    /** Thrown if the replyTo of a subscription change message is not a single message recipient */
    class InvalidSubscriber : NodeMapError()

    /** Thrown if a change arrives which is of an unknown type */
    class UnknownChangeType : NodeMapError()
}

data class LastAcknowledgeInfo(val mapVersion: Int)
data class NodeRegistrationInfo(val reg: NodeRegistration, val mapVersion: Int)
