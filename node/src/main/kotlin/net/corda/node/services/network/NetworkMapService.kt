package net.corda.node.services.network

import net.corda.core.CordaException
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.ServiceType
import net.corda.node.services.api.AbstractNodeService
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.messaging.MessageHandlerRegistration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.ServiceRequestMessage
import net.corda.node.services.messaging.createMessage
import net.corda.node.services.network.NetworkMapService.*
import net.corda.node.services.network.NetworkMapService.Companion.FETCH_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.PUSH_ACK_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.PUSH_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.QUERY_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.REGISTER_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_TOPIC
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.node.utilities.AddOrRemove.REMOVE
import java.io.IOException
import java.security.PublicKey
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
        val DEFAULT_EXPIRATION_PERIOD: Period = Period.ofWeeks(4)
        const val FETCH_TOPIC = "platform.network_map.fetch"
        const val QUERY_TOPIC = "platform.network_map.query"
        const val REGISTER_TOPIC = "platform.network_map.register"
        const val SUBSCRIPTION_TOPIC = "platform.network_map.subscribe"
        // Base topic used when pushing out updates to the network map. Consumed, for example, by the map cache.
        // When subscribing to these updates, remember they must be acknowledged
        const val PUSH_TOPIC = "platform.network_map.push"
        // Base topic for messages acknowledging pushed updates
        const val PUSH_ACK_TOPIC = "platform.network_map.push_ack"

        val type = ServiceType.networkMap
    }

    data class FetchMapRequest(val subscribe: Boolean,
                               val ifChangedSinceVersion: Int?,
                               override val replyTo: SingleMessageRecipient,
                               override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    @CordaSerializable
    data class FetchMapResponse(val nodes: List<NodeRegistration>?, val version: Int)

    data class QueryIdentityRequest(val identity: PartyAndCertificate,
                                    override val replyTo: SingleMessageRecipient,
                                    override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    @CordaSerializable
    data class QueryIdentityResponse(val node: NodeInfo?)

    // TODO Rename this RegistractionChangeRequest or similar (and related classes)
    data class RegistrationRequest(val wireReg: WireNodeRegistration,
                                   override val replyTo: SingleMessageRecipient,
                                   override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    /** If [error] is null then the registration was successful. If not null then it wasn't and it explains why */
    @CordaSerializable
    data class RegistrationResponse(val error: String?)

    data class SubscribeRequest(val subscribe: Boolean,
                                override val replyTo: SingleMessageRecipient,
                                override val sessionID: Long = random63BitValue()) : ServiceRequestMessage

    @CordaSerializable
    data class SubscribeResponse(val confirmed: Boolean)

    @CordaSerializable
    data class Update(val wireReg: WireNodeRegistration, val mapVersion: Int, val replyTo: MessageRecipients)

    @CordaSerializable
    data class UpdateAcknowledge(val mapVersion: Int, val replyTo: MessageRecipients)
}

object NullNetworkMapService : NetworkMapService

@ThreadSafe
class InMemoryNetworkMapService(network: MessagingService, ownPlatformVersion: Int, networkMapCache: NetworkMapCacheInternal, minimumPlatformVersion: Int)
    : AbstractNetworkMapService(network, ownPlatformVersion, networkMapCache, minimumPlatformVersion) {

    override val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo> = ConcurrentHashMap()
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
abstract class AbstractNetworkMapService(network: MessagingService,
                                         ownPlatformVersion: Int,
                                         private val networkMapCache: NetworkMapCacheInternal,
                                         private val minimumPlatformVersion: Int) : NetworkMapService, AbstractNodeService(network) {
    companion object {
        /**
         * Maximum credible size for a registration request. Generally requests are around 2000-6000 bytes, so this gives a
         * 10 times overhead.
         */
        private const val MAX_SIZE_REGISTRATION_REQUEST_BYTES = 40000
        private val logger = loggerFor<AbstractNetworkMapService>()
    }

    protected abstract val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo>

    // Map from subscriber address, to most recently acknowledged update map version.
    protected abstract val subscribers: ThreadBox<MutableMap<SingleMessageRecipient, LastAcknowledgeInfo>>

    protected val _mapVersion = AtomicInteger(0)

    @VisibleForTesting
    val mapVersion: Int
        get() = _mapVersion.get()

    /** Maximum number of unacknowledged updates to send to a node before automatically unregistering them for updates */
    val maxUnacknowledgedUpdates = 10

    private val handlers = ArrayList<MessageHandlerRegistration>()

    init {
        require(minimumPlatformVersion >= 1) { "minimumPlatformVersion cannot be less than 1" }
        require(minimumPlatformVersion <= ownPlatformVersion) {
            "minimumPlatformVersion cannot be greater than the node's own version"
        }
    }

    protected fun setup() {
        // Register message handlers
        handlers += addMessageHandler(FETCH_TOPIC) { req: FetchMapRequest -> processFetchAllRequest(req) }
        handlers += addMessageHandler(QUERY_TOPIC) { req: QueryIdentityRequest -> processQueryRequest(req) }
        handlers += addMessageHandler(REGISTER_TOPIC) { req: RegistrationRequest -> processRegistrationRequest(req) }
        handlers += addMessageHandler(SUBSCRIPTION_TOPIC) { req: SubscribeRequest -> processSubscriptionRequest(req) }
        handlers += network.addMessageHandler(PUSH_ACK_TOPIC) { message, _ ->
            val req = message.data.deserialize<UpdateAcknowledge>()
            processAcknowledge(req)
        }
    }

    @VisibleForTesting
    fun unregisterNetworkHandlers() {
        for (handler in handlers) {
            network.removeMessageHandler(handler)
        }
        handlers.clear()
    }

    private fun addSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapException.InvalidSubscriber()
        subscribers.locked {
            if (!containsKey(subscriber)) {
                put(subscriber, LastAcknowledgeInfo(mapVersion))
            }
        }
    }

    private fun removeSubscriber(subscriber: MessageRecipients) {
        if (subscriber !is SingleMessageRecipient) throw NodeMapException.InvalidSubscriber()
        subscribers.locked { remove(subscriber) }
    }

    private fun processAcknowledge(request: UpdateAcknowledge) {
        if (request.replyTo !is SingleMessageRecipient) throw NodeMapException.InvalidSubscriber()
        subscribers.locked {
            val lastVersionAcked = this[request.replyTo]?.mapVersion
            if ((lastVersionAcked ?: 0) < request.mapVersion) {
                this[request.replyTo] = LastAcknowledgeInfo(request.mapVersion)
            }
        }
    }

    private fun processFetchAllRequest(request: FetchMapRequest): FetchMapResponse {
        if (request.subscribe) {
            addSubscriber(request.replyTo)
        }
        val currentVersion = mapVersion
        val nodeRegistrations = if (request.ifChangedSinceVersion == null || request.ifChangedSinceVersion < currentVersion) {
            // We return back the current state of the entire map including nodes that have been removed
            ArrayList(nodeRegistrations.values.map { it.reg })  // Snapshot to avoid attempting to serialise Map internals
        } else {
            null
        }
        return FetchMapResponse(nodeRegistrations, currentVersion)
    }

    private fun processQueryRequest(request: QueryIdentityRequest): QueryIdentityResponse {
        val candidate = nodeRegistrations[request.identity]?.reg
        // If the most recent record we have is of the node being removed from the map, then it's considered
        // as no match.
        val node = if (candidate == null || candidate.type == REMOVE) null else candidate.node
        return QueryIdentityResponse(node)
    }

    private fun processRegistrationRequest(request: RegistrationRequest): RegistrationResponse {
        val requestSize = request.wireReg.raw.size
        logger.debug { "Received registration request of size: $requestSize" }
        if (requestSize > MAX_SIZE_REGISTRATION_REQUEST_BYTES) {
            return RegistrationResponse("Request is too big")
        }

        val change = try {
            request.wireReg.verified()
        } catch (e: SignatureException) {
            return RegistrationResponse("Invalid signature on request")
        } catch (e: IOException) {
            val msg = "Unexpected IO exception: ${e.message}"
            logger.error(msg, e)
            return RegistrationResponse(msg)
        }
        val node = change.node
        // Get identity from signature on node's registration and use it as an index.
        val identity = node.legalIdentitiesAndCerts.singleOrNull { request.wireReg.sig.by == it.owningKey }
        identity ?: return RegistrationResponse("Key from signature on the node registration wasn't found in NodeInfo")

        if (node.platformVersion < minimumPlatformVersion) {
            return RegistrationResponse("Minimum platform version requirement not met: $minimumPlatformVersion")
        }

        // Update the current value atomically, so that if multiple updates come
        // in on different threads, there is no risk of a race condition while checking
        // sequence numbers.
        val registrationInfo = try {
            nodeRegistrations.compute(identity) { _, existing: NodeRegistrationInfo? ->
                require(!((existing == null || existing.reg.type == REMOVE) && change.type == REMOVE)) {
                    "Attempting to de-register unknown node"
                }
                require(existing == null || existing.reg.serial < change.serial) { "Serial value is too small" }
                NodeRegistrationInfo(change, _mapVersion.incrementAndGet())
            }
        } catch (e: IllegalArgumentException) {
            return RegistrationResponse(e.message)
        }

        notifySubscribers(request.wireReg, registrationInfo!!.mapVersion)

        // Update the local cache
        // TODO: Once local messaging is fixed, this should go over the network layer as it does to other
        // subscribers
        when (change.type) {
            ADD -> {
                logger.info("Added node ${node.addresses} to network map")
                networkMapCache.addNode(change.node)
            }
            REMOVE -> {
                logger.info("Removed node ${node.addresses} from network map")
                networkMapCache.removeNode(change.node)
            }
        }

        return RegistrationResponse(null)
    }

    private fun notifySubscribers(wireReg: WireNodeRegistration, newMapVersion: Int) {
        // TODO: Once we have a better established messaging system, we can probably send
        //       to a MessageRecipientGroup that nodes join/leave, rather than the network map
        //       service itself managing the group
        val update = NetworkMapService.Update(wireReg, newMapVersion, network.myAddress).serialize().bytes
        val message = network.createMessage(PUSH_TOPIC, data = update)

        subscribers.locked {
            // Remove any stale subscribers
            values.removeIf { (mapVersion) -> newMapVersion - mapVersion > maxUnacknowledgedUpdates }
            // TODO: introduce some concept of time in the condition to avoid unsubscribes when there's a message burst.
            keys.forEach { recipient -> network.send(message, recipient) }
        }
    }

    private fun processSubscriptionRequest(request: SubscribeRequest): SubscribeResponse {
        if (request.subscribe) {
            addSubscriber(request.replyTo)
        } else {
            removeSubscriber(request.replyTo)
        }
        return SubscribeResponse(true)
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
@CordaSerializable
data class NodeRegistration(val node: NodeInfo, val serial: Long, val type: AddOrRemove, var expires: Instant) {
    /**
     * Build a node registration in wire format.
     */
    fun toWire(keyManager: KeyManagementService, publicKey: PublicKey): WireNodeRegistration {
        val regSerialized = this.serialize()
        val regSig = keyManager.sign(regSerialized.bytes, publicKey)

        return WireNodeRegistration(regSerialized, regSig)
    }

    override fun toString(): String = "$node #$serial ($type)"
}

/**
 * A node registration and its signature as a pair.
 */
@CordaSerializable
class WireNodeRegistration(raw: SerializedBytes<NodeRegistration>, sig: DigitalSignature.WithKey) : SignedData<NodeRegistration>(raw, sig) {
    @Throws(IllegalArgumentException::class)
    override fun verifyData(data: NodeRegistration) {
        // Check that the registration is fulfilled by any of node's identities.
        // TODO It may cause some problems with distributed services? We loose node's main identity. Should be all signatures instead of isFulfilledBy?
        require(data.node.legalIdentitiesAndCerts.any { it.owningKey.isFulfilledBy(sig.by) })
    }
}

@CordaSerializable
sealed class NodeMapException : CordaException("Network Map Protocol Error") {

    /** Thrown if the signature on the node info does not match the public key for the identity */
    class InvalidSignature : NodeMapException()

    /** Thrown if the replyTo of a subscription change message is not a single message recipient */
    class InvalidSubscriber : NodeMapException()
}

@CordaSerializable
data class LastAcknowledgeInfo(val mapVersion: Int)

@CordaSerializable
data class NodeRegistrationInfo(val reg: NodeRegistration, val mapVersion: Int)
