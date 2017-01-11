package net.corda.node.services.statemachine

import net.corda.node.services.statemachine.StateMachineManager.FlowSession

// TODO revisit when Kotlin 1.1 is released and data classes can extend other classes
interface FlowIORequest {
    // This is used to identify where we suspended, in case of message mismatch errors and other things where we
    // don't have the original stack trace because it's in a suspended fiber.
    val stackTraceInCaseOfProblems: StackSnapshot
    val session: FlowSession
}

interface SendRequest : FlowIORequest {
    val message: SessionMessage
}

interface ReceiveRequest<T : SessionMessage> : FlowIORequest {
    val receiveType: Class<T>
}

data class SendAndReceive<T : SessionMessage>(override val session: FlowSession,
                                              override val message: SessionMessage,
                                              override val receiveType: Class<T>) : SendRequest, ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class ReceiveOnly<T : SessionMessage>(override val session: FlowSession,
                                           override val receiveType: Class<T>) : ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class SendOnly(override val session: FlowSession, override val message: SessionMessage) : SendRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")
