package com.r3corda.node.services.statemachine

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.TopicSession

// TODO: Clean this up
sealed class FiberRequest(val topic: String,
                          val destination: Party?,
                          val sessionIDForSend: Long,
                          val sessionIDForReceive: Long,
                          val payload: Any?) {
    // This is used to identify where we suspended, in case of message mismatch errors and other things where we
    // don't have the original stack trace because it's in a suspended fiber.
    @Transient
    val stackTraceInCaseOfProblems: StackSnapshot? = StackSnapshot()

    val receiveTopicSession: TopicSession
        get() = TopicSession(topic, sessionIDForReceive)


    override fun equals(other: Any?): Boolean
        = if (other is FiberRequest) {
            topic == other.topic
                    && destination == other.destination
                    && sessionIDForSend == other.sessionIDForSend
                    && sessionIDForReceive == other.sessionIDForReceive
                    && payload == other.payload
        } else
            false

    override fun hashCode(): Int {
        var hash = 1L

        hash = (hash * 31) + topic.hashCode()
        hash = (hash * 31) + if (destination == null)
            0
        else
            destination.hashCode()
        hash = (hash * 31) + sessionIDForReceive
        hash = (hash * 31) + sessionIDForReceive
        hash = (hash * 31) + if (payload == null)
            0
        else
            payload.hashCode()

        return hash.toInt()
    }

    /**
     * A fiber which is expecting a message response.
     */
    class ExpectingResponse<R : Any>(
            topic: String,
            destination: Party?,
            sessionIDForSend: Long,
            sessionIDForReceive: Long,
            obj: Any?,
            type: Class<R>
    ) : FiberRequest(topic, destination, sessionIDForSend, sessionIDForReceive, obj) {
        private val responseTypeName: String = type.name

        override fun equals(other: Any?): Boolean
                = if (other is ExpectingResponse<*>) {
            super.equals(other)
                    && responseTypeName == other.responseTypeName
        } else
            false

        override fun toString(): String {
            return "Expecting response via topic ${receiveTopicSession} of type ${responseTypeName}"
        }

        // We have to do an unchecked cast, but unless the serialized form is damaged, this was
        // correct when the request was instantiated
        @Suppress("UNCHECKED_CAST")
        val responseType: Class<R>
            get() = Class.forName(responseTypeName) as Class<R>
    }

    class NotExpectingResponse(
            topic: String,
            destination: Party,
            sessionIDForSend: Long,
            obj: Any?
    ) : FiberRequest(topic, destination, sessionIDForSend, -1, obj)
}