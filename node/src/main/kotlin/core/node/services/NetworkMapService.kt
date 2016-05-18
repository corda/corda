package core.node.services

import co.paralleluniverse.common.util.VisibleForTesting
import core.crypto.Party
import core.ThreadBox
import core.crypto.DigitalSignature
import core.crypto.SecureHash
import core.crypto.SignedData
import core.crypto.signWithECDSA
import core.messaging.MessageRecipients
import core.messaging.MessagingService
import core.messaging.SingleMessageRecipient
import core.node.NodeInfo
import core.node.subsystems.NetworkMapCache
import core.node.subsystems.TOPIC_DEFAULT_POSTFIX
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.AddOrRemove
import org.slf4j.LoggerFactory
import protocols.AbstractRequestMessage
import java.security.PrivateKey
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
    object Type : ServiceType("corda.network_map")

    companion object {
        val DEFAULT_EXPIRATION_PERIOD = Period.ofWeeks(4)
        val FETCH_PROTOCOL_TOPIC = "platform.network_map.fetch"
        val QUERY_PROTOCOL_TOPIC = "platform.network_map.query"
        val REGISTER_PROTOCOL_TOPIC = "platform.network_map.register"
        val SUBSCRIPTION_PROTOCOL_TOPIC = "platform.network_map.subscribe"
        // Base topic used when pushing out updates to the network map. Consumed, for example, by the map cache.
        // When subscribing to these updates, remember they must be acknowledged
        val PUSH_PROTOCOL_TOPIC = "platform.network_map.push"
        // Base topic for messages acknowledging pushed updates
        val PUSH_ACK_PROTOCOL_TOPIC = "platform.network_map.push_ack"

        val logger = LoggerFactory.getLogger(NetworkMapService::class.java)
    }

    val nodes: List<NodeInfo>

    class FetchMapRequest(val subscribe: Boolean, val ifChangedSinceVersion: Int?, replyTo: MessageRecipients, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
    data class FetchMapResponse(val nodes: Collection<NodeRegistration>?, val version: Int)
    class QueryIdentityRequest(val identity: Party, replyTo: MessageRecipients, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
    data class QueryIdentityResponse(val node: NodeInfo?)
    class RegistrationRequest(val wireReg: WireNodeRegistration, replyTo: MessageRecipients, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
    data class RegistrationResponse(val success: Boolean)
    class SubscribeRequest(val subscribe: Boolean, replyTo: MessageRecipients, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
    data class SubscribeResponse(val confirmed: Boolean)
    data class Update(val wireReg: WireNodeRegistration, val replyTo: MessageRecipients)
    data class UpdateAcknowledge(val wireRegHash: SecureHash, val replyTo: MessageRecipients)
}

@ThreadSafe
class InMemoryNetworkMapService(net: MessagingService, home: NodeRegistration, val cache: NetworkMapCache) : NetworkMapService, AbstractNodeService(net) {
    private val registeredNodes = ConcurrentHashMap<Party, NodeRegistration>()
    // Map from subscriber address, to a list of unacknowledged updates
    private val subscribers = ThreadBox(mutableMapOf<SingleMessageRecipient, MutableList<SecureHash>>())
    private val mapVersion = AtomicInteger(1)
    /** Maximum number of unacknowledged updates to send to a node before automatically unregistering them for updates */
    val maxUnacknowledgedUpdates = 10
    /**
     * Maximum credible size for a registration request. Generally requests are around 500-600 bytes, so this gives a
     * 10 times overhead.
     */
    val maxSizeRegistrationRequestBytes = 5500

    // Filter reduces this to the entries that add a node to the map
    override val nodes: List<NodeInfo>
        get() = registeredNodes.mapNotNull { if (it.value.type == AddOrRemove.ADD) it.value.node else null }

    init {
        // Register the local node with the service
        val homeIdentity = home.node.identity
        registeredNodes[homeIdentity] = home

        // Register message handlers
        addMessageHandler(NetworkMapService.FETCH_PROTOCOL_TOPIC,
                { req: NetworkMapService.FetchMapRequest -> processFetchAllRequest(req) }
        )
        addMessageHandler(NetworkMapService.QUERY_PROTOCOL_TOPIC,
                { req: NetworkMapService.QueryIdentityRequest -> processQueryRequest(req) }
        )
        addMessageHandler(NetworkMapService.REGISTER_PROTOCOL_TOPIC,
                { req: NetworkMapService.RegistrationRequest -> processRegistrationChangeRequest(req) }
        )
        addMessageHandler(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC,
                { req: NetworkMapService.SubscribeRequest -> processSubscriptionRequest(req) }
        )
        net.addMessageHandler(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC + TOPIC_DEFAULT_POSTFIX, null) { message, r ->
            val req = message.data.deserialize<NetworkMapService.UpdateAcknowledge>()
            processAcknowledge(req)
        }
    }

    private fun addSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapError.InvalidSubscriber()
        subscribers.locked {
            if (!containsKey(subscriber)) {
                put(subscriber, mutableListOf<SecureHash>())
            }
        }
    }

    private fun removeSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapError.InvalidSubscriber()
        subscribers.locked { remove(subscriber) }
    }

    @VisibleForTesting
    fun getUnacknowledgedCount(subscriber: SingleMessageRecipient): Int?
            = subscribers.locked { get(subscriber)?.count() }

    @VisibleForTesting
    fun notifySubscribers(wireReg: WireNodeRegistration) {
        // TODO: Once we have a better established messaging system, we can probably send
        // to a MessageRecipientGroup that nodes join/leave, rather than the network map
        // service itself managing the group
        val update = NetworkMapService.Update(wireReg, net.myAddress).serialize().bits
        val topic = NetworkMapService.PUSH_PROTOCOL_TOPIC + TOPIC_DEFAULT_POSTFIX
        val message = net.createMessage(topic, update)

        subscribers.locked {
            val toRemove = mutableListOf<SingleMessageRecipient>()
            val hash = SecureHash.sha256(wireReg.raw.bits)
            forEach { subscriber: Map.Entry<SingleMessageRecipient, MutableList<SecureHash>> ->
                val unacknowledged = subscriber.value
                if (unacknowledged.count() < maxUnacknowledgedUpdates) {
                    unacknowledged.add(hash)
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
        subscribers.locked {
            this[req.replyTo]?.remove(req.wireRegHash)
        }
    }

    @VisibleForTesting
    fun processFetchAllRequest(req: NetworkMapService.FetchMapRequest): NetworkMapService.FetchMapResponse {
        if (req.subscribe) {
            addSubscriber(req.replyTo)
        }
        val ver = mapVersion.get()
        if (req.ifChangedSinceVersion == null || req.ifChangedSinceVersion < ver) {
            val nodes = ArrayList(registeredNodes.values)  // Snapshot to avoid attempting to serialise ConcurrentHashMap internals
            return NetworkMapService.FetchMapResponse(nodes, ver)
        } else {
            return NetworkMapService.FetchMapResponse(null, ver)
        }
    }

    @VisibleForTesting
    fun processQueryRequest(req: NetworkMapService.QueryIdentityRequest): NetworkMapService.QueryIdentityResponse {
        val candidate = registeredNodes[req.identity]

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
        } catch(e: java.security.SignatureException) {
            throw NodeMapError.InvalidSignature()
        }
        val node = change.node

        var changed: Boolean = false
        // Update the current value atomically, so that if multiple updates come
        // in on different threads, there is no risk of a race condition while checking
        // sequence numbers.
        registeredNodes.compute(node.identity, { mapKey: Party, existing: NodeRegistration? ->
            changed = existing == null || existing.serial < change.serial
            if (changed) {
                when (change.type) {
                    AddOrRemove.ADD -> change
                    AddOrRemove.REMOVE -> change
                    else -> throw NodeMapError.UnknownChangeType()
                }
            } else {
                existing
            }
        })
        if (changed) {
            notifySubscribers(req.wireReg)

            // Update the local cache
            // TODO: Once local messaging is fixed, this should go over the network layer as it does to other
            // subscribers
            when (change.type) {
                AddOrRemove.ADD -> {
                    NetworkMapService.logger.info("Added node ${node.address} to network map")
                    cache.addNode(change.node)
                }
                AddOrRemove.REMOVE -> {
                    NetworkMapService.logger.info("Removed node ${node.address} from network map")
                    cache.removeNode(change.node)
                }
            }

            mapVersion.incrementAndGet()
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
        val regSig = privateKey.signWithECDSA(regSerialized.bits, node.identity.owningKey)

        return WireNodeRegistration(regSerialized, regSig)
    }

    override fun toString() : String = "$node #${serial} (${type})"
}

/**
 * A node registration and its signature as a pair.
 */
class WireNodeRegistration(raw: SerializedBytes<NodeRegistration>, sig: DigitalSignature.WithKey) : SignedData<NodeRegistration>(raw, sig) {
    @Throws(IllegalArgumentException::class)
    override fun verifyData(data: NodeRegistration) {
        require(data.node.identity.owningKey == sig.by)
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
