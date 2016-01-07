/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import com.google.common.util.concurrent.ListenableFuture
import core.serialization.serialize
import java.time.Instant
import java.util.concurrent.Executor
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
     * The provided function will be invoked for each received message whose topic matches the given string, on the given
     * executor. The topic can be the empty string to match all messages.
     *
     * If no executor is received then the callback will run on threads provided by the messaging service, and the
     * callback is expected to be thread safe as a result.
     *
     * The returned object is an opaque handle that may be used to un-register handlers later with [removeMessageHandler].
     * The handle is passed to the callback as well, to avoid race conditions whereby the callback wants to unregister
     * itself and yet addMessageHandler hasn't returned the handle yet.
     */
    fun addMessageHandler(topic: String = "", executor: Executor? = null, callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration

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
     * There is no way to know if a message has been received. If your protocol requires this, you need the recipient
     * to send an ACK message back.
     */
    fun send(message: Message, target: MessageRecipients)

    fun stop()

    /**
     * Returns an initialised [Message] with the current time, etc, already filled in.
     */
    fun createMessage(topic: String, data: ByteArray): Message

    /** Returns an address that refers to this node */
    val myAddress: SingleMessageRecipient
}

/**
 * Registers a handler for the given topic that runs the given callback with the message and then removes itself. This
 * is useful for one-shot handlers that aren't supposed to stick around permanently. Note that this callback doesn't
 * take the registration object, unlike the callback to [MessagingService.addMessageHandler].
 */
fun MessagingService.runOnNextMessage(topic: String = "", executor: Executor? = null, callback: (Message) -> Unit) {
    addMessageHandler(topic, executor) { msg, reg ->
        callback(msg)
        removeMessageHandler(reg)
    }
}

fun MessagingService.send(topic: String, to: MessageRecipients, obj: Any) = send(createMessage(topic, obj.serialize().bits), to)

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
 * A message is defined, at this level, to be a (topic, timestamp, byte arrays) triple, where the topic is a string in
 * Java-style reverse dns form, with "platform." being a prefix reserved by the platform for its own use. Vendor
 * specific messages can be defined, but use your domain name as the prefix e.g. "uk.co.bigbank.messages.SomeMessage".
 *
 * The debugTimestamp field is intended to aid in tracking messages as they flow across the network, likewise, the
 * message ID is intended to be an ad-hoc way to identify a message sent in the system through debug logs and so on.
 * These IDs and timestamps should not be assumed to be globally unique, although due to the nanosecond precision of
 * the timestamp field they probably will be, even if an implementation just uses a hash prefix as the message id.
 */
interface Message {
    val topic: String
    val data: ByteArray
    val debugTimestamp: Instant
    val debugMessageID: String
    fun serialise(): ByteArray
}

/** A singleton that's useful for validating topic strings */
object TopicStringValidator {
    private val regex = "[a-zA-Z0-9.]+".toPattern()
    /** @throws IllegalArgumentException if the given topic contains invalid characters */
    fun check(tag: String) = require(regex.matcher(tag).matches())
}

/** The interface for a group of message recipients (which may contain only one recipient) */
interface MessageRecipients
/** A base class for the case of point-to-point messages */
interface SingleMessageRecipient : MessageRecipients
/** A base class for a set of recipients specifically identified by the sender. */
interface MessageRecipientGroup : MessageRecipients
/** A special base class for the set of all possible recipients, without having to identify who they all are. */
interface AllPossibleRecipients : MessageRecipients