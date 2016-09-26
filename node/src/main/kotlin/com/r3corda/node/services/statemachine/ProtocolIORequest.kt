package com.r3corda.node.services.statemachine

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.TopicSession
import java.util.*

// TODO revisit when Kotlin 1.1 is released and data classes can extend other classes
interface ProtocolIORequest {
    // This is used to identify where we suspended, in case of message mismatch errors and other things where we
    // don't have the original stack trace because it's in a suspended fiber.
    val stackTraceInCaseOfProblems: StackSnapshot
    val topic: String
}

interface SendRequest : ProtocolIORequest {
    val destination: Party
    val payload: Any
    val sendSessionID: Long
    val uniqueMessageId: UUID
}

interface ReceiveRequest<T> : ProtocolIORequest {
    val receiveType: Class<T>
    val receiveSessionID: Long
    val receiveTopicSession: TopicSession get() = TopicSession(topic, receiveSessionID)
}

data class SendAndReceive<T>(override val topic: String,
                             override val destination: Party,
                             override val payload: Any,
                             override val sendSessionID: Long,
                             override val uniqueMessageId: UUID,
                             override val receiveType: Class<T>,
                             override val receiveSessionID: Long) : SendRequest, ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class ReceiveOnly<T>(override val topic: String,
                          override val receiveType: Class<T>,
                          override val receiveSessionID: Long) : ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class SendOnly(override val destination: Party,
                    override val topic: String,
                    override val payload: Any,
                    override val sendSessionID: Long,
                    override val uniqueMessageId: UUID) : SendRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")
