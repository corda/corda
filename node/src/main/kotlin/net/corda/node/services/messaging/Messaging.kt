package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.annotations.serialization.Serializable
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import java.time.Instant
import java.util.*
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
    /**
     * The provided function will be invoked for each received message whose topic and session matches.  The callback
     * will run on the main server thread provided when the messaging service is constructed, and a database
     * transaction is set up for you automatically.
     *
     * The returned object is an opaque handle that may be used to un-register handlers later with [removeMessageHandler].
     * The handle is passed to the callback as well, to avoid race conditions whereby the callback wants to unregister
     * itself and yet addMessageHandler hasn't returned the handle yet.
     *
     * @param topic identifier for the topic to listen for messages arriving on.
     */
    fun addMessageHandler(topic: String, callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration

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
     *     Note that this feature should only be used when the target is an idempotent distributed service, e.g. a notary.
     * @param sequenceKey an object that may be used to enable a parallel [MessagingService] implementation. Two
     *     subsequent send()s with the same [sequenceKey] (up to equality) are guaranteed to be delivered in the same
     *     sequence the send()s were called. By default this is chosen conservatively to be [target].
     */
    @Suspendable
    fun send(
            message: Message,
            target: MessageRecipients,
            retryId: Long? = null,
            sequenceKey: Any = target,
            additionalHeaders: Map<String, String> = emptyMap()
    )

    /** A message with a target and sequenceKey specified. */
    data class AddressedMessage(
            val message: Message,
            val target: MessageRecipients,
            val retryId: Long? = null,
            val sequenceKey: Any = target
    )

    /**
     * Sends a list of messages to the specified recipients. This function allows for an efficient batching
     * implementation.
     *
     * @param addressedMessages The list of messages together with the recipients, retry ids and sequence keys.
     */
    @Suspendable
    fun send(addressedMessages: List<AddressedMessage>)

    /** Cancels the scheduled message redelivery for the specified [retryId] */
    fun cancelRedelivery(retryId: Long)

    /**
     * Returns an initialised [Message] with the current time, etc, already filled in.
     *
     * @param topicSession identifier for the topic and session the message is sent to.
     * @param additionalProperties optional additional message headers.
     * @param topic identifier for the topic the message is sent to.
     */
    fun createMessage(topic: String, data: ByteArray, deduplicationId: String = UUID.randomUUID().toString()): Message

    /** Given information about either a specific node or a service returns its corresponding address */
    fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients

    /** Returns an address that refers to this node. */
    val myAddress: SingleMessageRecipient
}


fun MessagingService.send(topicSession: String, payload: Any, to: MessageRecipients, deduplicationId: String = UUID.randomUUID().toString(), retryId: Long? = null)
        = send(createMessage(topicSession, payload.serialize().bytes, deduplicationId), to, retryId)

interface MessageHandlerRegistration

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
@Serializable
interface Message {
    val topic: String
    val data: ByteSequence
    val debugTimestamp: Instant
    val uniqueMessageId: String
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

