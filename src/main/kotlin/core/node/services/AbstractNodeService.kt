package core.node.services

import core.messaging.Message
import core.messaging.MessagingService
import core.node.subsystems.TOPIC_DEFAULT_POSTFIX
import core.serialization.deserialize
import core.serialization.serialize
import protocols.AbstractRequestMessage
import javax.annotation.concurrent.ThreadSafe

/**
 * Abstract superclass for services that a node can host, which provides helper functions.
 */
@ThreadSafe
abstract class AbstractNodeService(val net: MessagingService) {

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are caught and passed to the provided consumer.
     *
     * @param topic the topic, without the default session ID postfix (".0)
     * @param handler a function to handle the deserialised request and return a response
     * @param exceptionConsumer a function to which any thrown exception is passed.
     */
    protected inline fun <reified Q : AbstractRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R,
                              crossinline exceptionConsumer: (Message, Exception) -> Unit) {
        net.addMessageHandler(topic + TOPIC_DEFAULT_POSTFIX, null) { message, r ->
            try {
                val req = message.data.deserialize<Q>()
                val data = handler(req)
                val msg = net.createMessage(topic + "." + req.sessionID, data.serialize().bits)
                net.send(msg, req.replyTo)
            } catch(e: Exception) {
                exceptionConsumer(message, e)
            }
        }
    }

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code. Exceptions are propagated to the messaging layer.
     *
     * @param topic the topic, without the default session ID postfix (".0)
     * @param handler a function to handle the deserialised request and return a response
     */
    protected inline fun <reified Q : AbstractRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R) {
        net.addMessageHandler(topic + TOPIC_DEFAULT_POSTFIX, null) { message, r ->
            val req = message.data.deserialize<Q>()
            val data = handler(req)
            val msg = net.createMessage(topic + "." + req.sessionID, data.serialize().bits)
            net.send(msg, req.replyTo)
        }
    }
}
