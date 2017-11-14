package net.corda.node.services.api

import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.messaging.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Abstract superclass for services that a node can host, which provides helper functions.
 */
@ThreadSafe
abstract class AbstractNodeService(val network: MessagingService) : SingletonSerializeAsToken() {
    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are caught and passed to the provided consumer.  If you just want a simple
     * acknowledgement response with no content, use [net.corda.core.messaging.Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit)
     * @param exceptionConsumer a function to which any thrown exception is passed.
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R,
                              crossinline exceptionConsumer: (Message, Exception) -> Unit): MessageHandlerRegistration {
        return network.addMessageHandler(topic, MessagingService.DEFAULT_SESSION_ID) { message, _ ->
            try {
                val request = message.data.deserialize<Q>()
                val response = handler(request)
                // If the return type R is Unit, then do not send a response
                if (response.javaClass != Unit.javaClass) {
                    val msg = network.createMessage(topic, request.sessionID, response.serialize().bytes)
                    network.send(msg, request.replyTo)
                }
            } catch (e: Exception) {
                exceptionConsumer(message, e)
            }
        }
    }

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are propagated to the messaging layer.  If you just want a simple
     * acknowledgement response with no content, use [net.corda.core.messaging.Ack].
     *
     * @param topic the topic, without the default session ID postfix (".0).
     * @param handler a function to handle the deserialised request and return an optional response (if return type not Unit).
     */
    protected inline fun <reified Q : ServiceRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R): MessageHandlerRegistration {
        return addMessageHandler(topic, handler, { _: Message, exception: Exception -> throw exception })
    }

}
