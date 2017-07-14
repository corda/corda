package net.corda.node.services.messaging

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable
import net.corda.node.services.api.DEFAULT_SESSION_ID

/**
 * Abstract superclass for request messages sent to services which expect a reply.
 */
@CordaSerializable
interface ServiceRequestMessage {
    val sessionID: Long
    val replyTo: SingleMessageRecipient
}

/**
 * Sends a [ServiceRequestMessage] to [target] and returns a [ListenableFuture] of the response.
 * @param R The type of the response.
 */
fun <R : Any> MessagingService.sendRequest(topic: String,
                                           request: ServiceRequestMessage,
                                           target: MessageRecipients): ListenableFuture<R> {
    val responseFuture = onNext<R>(topic, request.sessionID)
    send(topic, DEFAULT_SESSION_ID, request, target)
    return responseFuture
}
