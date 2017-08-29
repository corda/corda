package net.corda.node.services.messaging

import net.corda.core.concurrent.CordaFuture
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable

/**
 * Abstract superclass for request messages sent to services which expect a reply.
 */
@CordaSerializable
interface ServiceRequestMessage {
    val sessionID: Long
    val replyTo: SingleMessageRecipient
}

/**
 * Sends a [ServiceRequestMessage] to [target] and returns a [CordaFuture] of the response.
 * @param R The type of the response.
 */
fun <R : Any> MessagingService.sendRequest(topic: String,
                                           request: ServiceRequestMessage,
                                           target: MessageRecipients): CordaFuture<R> {
    val responseFuture = onNext<R>(topic, request.sessionID)
    send(topic, MessagingService.DEFAULT_SESSION_ID, request, target)
    return responseFuture
}
