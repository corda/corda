package core.node.services

import core.messaging.Message
import core.messaging.MessagingService
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
     * Postfix for base topics when sending a request to a service.
     */
    protected val topicDefaultPostfix = ".0"

    /**
     * Register a handler for a message topic. In comparison to using net.addMessageHandler() this manages a lot of
     * common boilerplate code.
     */
    protected inline fun <reified Q : AbstractRequestMessage, reified R : Any>
            addMessageHandler(topic: String,
                              crossinline handler: (Q) -> R,
                              crossinline exceptionHandler: (Message, Exception) -> Unit) {
        net.addMessageHandler(topic + topicDefaultPostfix, null) { message, r ->
            try {
                val req = message.data.deserialize<Q>()
                val data = handler(req)
                val msg = net.createMessage(topic + "." + req.sessionID, data.serialize().bits)
                net.send(msg, req.replyTo)
            } catch(e: Exception) {
                exceptionHandler(message, e)
            }
        }
    }
}