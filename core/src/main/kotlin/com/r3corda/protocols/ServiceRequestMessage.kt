package com.r3corda.protocols

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.messaging.onNext
import com.r3corda.core.messaging.send
import com.r3corda.core.node.services.DEFAULT_SESSION_ID

/**
 * Abstract superclass for request messages sent to services which expect a reply.
 */
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
                                           target: SingleMessageRecipient): ListenableFuture<R> {
    val responseFuture = onNext<R>(topic, request.sessionID)
    send(topic, DEFAULT_SESSION_ID, request, target)
    return responseFuture
}