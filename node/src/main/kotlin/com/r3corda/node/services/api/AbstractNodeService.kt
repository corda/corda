package com.r3corda.node.services.api

import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.TopicSession
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.protocols.ServiceRequestMessage
import javax.annotation.concurrent.ThreadSafe

/**
 * Abstract superclass for services that a node can host, which provides helper functions.
 */
@ThreadSafe
abstract class AbstractNodeService(val net: MessagingService, val networkMapCache: NetworkMapCache) : SingletonSerializeAsToken() {

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are caught and passed to the provided consumer.  If you just want a simple
     * acknowledgement response with no content, use [com.r3corda.core.messaging.Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit)
     * @param exceptionConsumer a function to which any thrown exception is passed.
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R,
                              crossinline exceptionConsumer: (Message, Exception) -> Unit) {
        net.addMessageHandler(topic, DEFAULT_SESSION_ID, null) { message, r ->
            try {
                val request = message.data.deserialize<Q>()
                val response = handler(request)
                // If the return type R is Unit, then do not send a response
                if (response.javaClass != Unit.javaClass) {
                    val msg = net.createMessage(topic, request.sessionID, response.serialize().bits)
                    net.send(msg, request.getReplyTo(networkMapCache))
                }
            } catch(e: Exception) {
                exceptionConsumer(message, e)
            }
        }
    }

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are propagated to the messaging layer.  If you just want a simple
     * acknowledgement response with no content, use [com.r3corda.core.messaging.Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit).
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R) {
        addMessageHandler(topic, handler, { message: Message, exception: Exception -> throw exception })
    }
}
