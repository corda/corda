package net.corda.node.services.api

import net.corda.core.messaging.Message
import net.corda.core.messaging.MessageHandlerRegistration
import net.corda.core.messaging.createMessage
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.flows.ServiceRequestMessage
import javax.annotation.concurrent.ThreadSafe

/**
 * Abstract superclass for services that a node can host, which provides helper functions.
 */
@ThreadSafe
abstract class AbstractNodeService(val services: ServiceHubInternal) : SingletonSerializeAsToken() {

    val net: MessagingServiceInternal get() = services.networkService

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are caught and passed to the provided consumer.  If you just want a simple
     * acknowledgement response with no content, use [Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit)
     * @param exceptionConsumer a function to which any thrown exception is passed.
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R,
                              crossinline exceptionConsumer: (Message, Exception) -> Unit): MessageHandlerRegistration {
        return net.addMessageHandler(topic, DEFAULT_SESSION_ID) { message, r ->
            try {
                val request = message.data.deserialize<Q>()
                val response = handler(request)
                // If the return type R is Unit, then do not send a response
                if (response.javaClass != Unit.javaClass) {
                    val msg = net.createMessage(topic, request.sessionID, response.serialize().bytes)
                    net.send(msg, request.replyTo)
                }
            } catch(e: Exception) {
                exceptionConsumer(message, e)
            }
        }
    }

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are propagated to the messaging layer.  If you just want a simple
     * acknowledgement response with no content, use [Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit).
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R): MessageHandlerRegistration {
        return addMessageHandler(topic, handler, { message: Message, exception: Exception -> throw exception })
    }

}
