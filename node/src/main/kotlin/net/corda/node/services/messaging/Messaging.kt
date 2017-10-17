package net.corda.node.services.messaging

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.concurrent.ThreadSafe

/**
 * A [MessagingService] sits at the boundary between a message routing / networking layer and the core platform code.
 *
 * A messaging system must provide the ability to send 1:many messages, potentially to an abstract "group", the
 * membership of which is defined elsewhere. Messages are atomic and the system guarantees that a sent message
 * _eventually_ will arrive in the exact form it was sent, however, messages can be arbitrarily re-ordered or delayed.
 *
 * Example implementations might be a custom P2P layer, Akka, Apache Kafka, etc. It is assumed that the message layer
 * is *reliable* and as such messages may be stored to disk once queued.
 */
@ThreadSafe
interface MessagingService {
    companion object {
        /**
         * Session ID to use for services listening for the first message in a session (before a
         * specific session ID has been established).
         */
        val DEFAULT_SESSION_ID = 0L
    }

    /**
     * The provided function will be invoked for each received message whose topic matches the given string.  The callback
     * will run on threads provided by the messaging service, and the callback is expected to be thread safe as a result.
     *
     * The returned object is an opaque handle that may be used to un-register handlers later with [removeMessageHandler].
     * The handle is passed to the callback as well, to avoid race conditions whereby the callback wants to unregister
     * itself and yet addMessageHandler hasn't returned the handle yet.
     *
     * @param topic identifier for the general subject of the message, for example "platform.network_map.fetch".
     * The topic can be the empty string to match all messages (session ID must be [DEFAULT_SESSION_ID]).
     * @param sessionID identifier for the session the message is part of. For services listening before
     * a session is established, use [DEFAULT_SESSION_ID].
     */
    fun addMessageHandler(topic: String = "", sessionID: Long = DEFAULT_SESSION_ID, callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration

    /**
     * The provided function will be invoked for each received message whose topic and session matches.  The callback
     * will run on the main server thread provided when the messaging service is constructed, and a database
     * transaction is set up for you automatically.
     *
     * The returned object is an opaque handle that may be used to un-register handlers later with [removeMessageHandler].
     * The handle is passed to the callback as well, to avoid race conditions whereby the callback wants to unregister
     * itself and yet addMessageHandler hasn't returned the handle yet.
     *
     * @param topicSession identifier for the topic and session to listen for messages arriving on.
     */
    fun addMessageHandler(topicSession: TopicSession, callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration

    /**
     * Removes a handler given the object returned from [addMessageHandler]. The callback will no longer be invoked once
     * this method has returned, although executions that are currently in flight will not be interrupted.
     *
     * @throws IllegalArgumentException if the given registration isn't valid for this messaging service.
     * @throws IllegalStateException if the given registration was already de-registered.
     */
    fun removeMessageHandler(registration: MessageHandlerRegistration)

    /**
     * Sends a message to the given receiver. The details of how receivers are identified is up to the messaging
     * implementation: the type system provides an opaque high level view, with more fine grained control being
     * available via type casting. Once this function returns the message is queued for delivery but not necessarily
     * delivered: if the recipients are offline then the message could be queued hours or days later.
     *
     * There is no way to know if a message has been received. If your flow requires this, you need the recipient
     * to send an ACK message back.
     *
     * @param retryId if provided the message will be scheduled for redelivery until [cancelRedelivery] is called for this id.
     * Note that this feature should only be used when the target is an idempotent distributed service, e.g. a notary.
     */
    fun send(message: Message, target: MessageRecipients, retryId: Long? = null)

    /** Cancels the scheduled message redelivery for the specified [retryId] */
    fun cancelRedelivery(retryId: Long)

    /**
     * Returns an initialised [Message] with the current time, etc, already filled in.
     *
     * @param topicSession identifier for the topic and session the message is sent to.
     */
    fun createMessage(topicSession: TopicSession, data: ByteArray, uuid: UUID = UUID.randomUUID()): Message

    /** Given information about either a specific node or a service returns its corresponding address */
    fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients

    /** Returns an address that refers to this node. */
    val myAddress: SingleMessageRecipient

    /**
     * Initiates shutdown: if called from a thread that isn't controlled by the executor passed to the constructor
     * then this will block until all in-flight messages have finished being handled and acknowledged. If called
     * from a thread that's a part of the [net.corda.node.utilities.AffinityExecutor] given to the constructor,
     * it returns immediately and shutdown is asynchronous.
     */
    fun stop()
}

/**
 * Returns an initialised [Message] with the current time, etc, already filled in.
 *
 * @param topic identifier for the general subject of the message, for example "platform.network_map.fetch".
 * Must not be blank.
 * @param sessionID identifier for the session the message is part of. For messages sent to services before the
 * construction of a session, use [DEFAULT_SESSION_ID].
 */
fun MessagingService.createMessage(topic: String, sessionID: Long = MessagingService.DEFAULT_SESSION_ID, data: ByteArray): Message
        = createMessage(TopicSession(topic, sessionID), data)

/**
 * Registers a handler for the given topic and session ID that runs the given callback with the message and then removes
 * itself. This is useful for one-shot handlers that aren't supposed to stick around permanently. Note that this callback
 * doesn't take the registration object, unlike the callback to [MessagingService.addMessageHandler], as the handler is
 * automatically deregistered before the callback runs.
 *
 * @param topic identifier for the general subject of the message, for example "platform.network_map.fetch".
 * The topic can be the empty string to match all messages (session ID must be [DEFAULT_SESSION_ID]).
 * @param sessionID identifier for the session the message is part of. For services listening before
 * a session is established, use [DEFAULT_SESSION_ID].
 */
fun MessagingService.runOnNextMessage(topic: String, sessionID: Long, callback: (ReceivedMessage) -> Unit)
        = runOnNextMessage(TopicSession(topic, sessionID), callback)

/**
 * Registers a handler for the given topic and session that runs the given callback with the message and then removes
 * itself. This is useful for one-shot handlers that aren't supposed to stick around permanently. Note that this callback
 * doesn't take the registration object, unlike the callback to [MessagingService.addMessageHandler].
 *
 * @param topicSession identifier for the topic and session to listen for messages arriving on.
 */
inline fun MessagingService.runOnNextMessage(topicSession: TopicSession, crossinline callback: (ReceivedMessage) -> Unit) {
    val consumed = AtomicBoolean()
    addMessageHandler(topicSession) { msg, reg ->
        removeMessageHandler(reg)
        check(!consumed.getAndSet(true)) { "Called more than once" }
        check(msg.topicSession == topicSession) { "Topic/session mismatch: ${msg.topicSession} vs $topicSession" }
        callback(msg)
    }
}

/**
 * Returns a [CordaFuture] of the next message payload ([Message.data]) which is received on the given topic and sessionId.
 * The payload is deserialized to an object of type [M]. Any exceptions thrown will be captured by the future.
 */
fun <M : Any> MessagingService.onNext(topic: String, sessionId: Long): CordaFuture<M> {
    val messageFuture = openFuture<M>()
    runOnNextMessage(topic, sessionId) { message ->
        messageFuture.capture {
            uncheckedCast(message.data.deserialize<Any>())
        }
    }
    return messageFuture
}

fun MessagingService.send(topic: String, sessionID: Long, payload: Any, to: MessageRecipients, uuid: UUID = UUID.randomUUID())
        = send(TopicSession(topic, sessionID), payload, to, uuid)

fun MessagingService.send(topicSession: TopicSession, payload: Any, to: MessageRecipients, uuid: UUID = UUID.randomUUID(), retryId: Long? = null)
        = send(createMessage(topicSession, payload.serialize().bytes, uuid), to, retryId)

/**
 * This class lets you start up a [MessagingService]. Its purpose is to stop you from getting access to the methods
 * on the messaging service interface until you have successfully started up the system. One of these objects should
 * be the only way to obtain a reference to a [MessagingService]. Startup may be a slow process: some implementations
 * may let you cast the returned future to an object that lets you get status info.
 *
 * A specific implementation of the controller class will have extra features that let you customise it before starting
 * it up.
 */
interface MessagingServiceBuilder<out T : MessagingService> {
    fun start(): ListenableFuture<out T>
}

interface MessageHandlerRegistration

/**
 * An identifier for the endpoint [MessagingService] message handlers listen at.
 *
 * @param topic identifier for the general subject of the message, for example "platform.network_map.fetch".
 * The topic can be the empty string to match all messages (session ID must be [DEFAULT_SESSION_ID]).
 * @param sessionID identifier for the session the message is part of. For services listening before
 * a session is established, use [DEFAULT_SESSION_ID].
 */
@CordaSerializable
data class TopicSession(val topic: String, val sessionID: Long = MessagingService.DEFAULT_SESSION_ID) {
    fun isBlank() = topic.isBlank() && sessionID == MessagingService.DEFAULT_SESSION_ID
    override fun toString(): String = "$topic.$sessionID"
}

/**
 * A message is defined, at this level, to be a (topic, timestamp, byte arrays) triple, where the topic is a string in
 * Java-style reverse dns form, with "platform." being a prefix reserved by the platform for its own use. Vendor
 * specific messages can be defined, but use your domain name as the prefix e.g. "uk.co.bigbank.messages.SomeMessage".
 *
 * The debugTimestamp field is intended to aid in tracking messages as they flow across the network, likewise, the
 * message ID is intended to be an ad-hoc way to identify a message sent in the system through debug logs and so on.
 * These IDs and timestamps should not be assumed to be globally unique, although due to the nanosecond precision of
 * the timestamp field they probably will be, even if an implementation just uses a hash prefix as the message id.
 */
@CordaSerializable
interface Message {
    val topicSession: TopicSession
    val data: ByteArray
    val debugTimestamp: Instant
    val uniqueMessageId: UUID
}

// TODO Have ReceivedMessage point to the TLS certificate of the peer, and [peer] would simply be the subject DN of that.
// The certificate would need to be serialised into the message header or just its fingerprint and then download it via RPC,
// or something like that.
interface ReceivedMessage : Message {
    /** The authenticated sender. */
    val peer: CordaX500Name
    /** Platform version of the sender's node. */
    val platformVersion: Int
}

/** A singleton that's useful for validating topic strings */
object TopicStringValidator {
    private val regex = "[a-zA-Z0-9.]+".toPattern()
    /** @throws IllegalArgumentException if the given topic contains invalid characters */
    fun check(tag: String) = require(regex.matcher(tag).matches())
}
