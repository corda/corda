package net.corda.node.services.statemachine

import net.corda.core.crypto.SecureHash

interface FlowIORequest {
    // This is used to identify where we suspended, in case of message mismatch errors and other things where we
    // don't have the original stack trace because it's in a suspended fiber.
    val stackTraceInCaseOfProblems: StackSnapshot
}

interface WaitingRequest : FlowIORequest

interface SessionedFlowIORequest : FlowIORequest {
    val session: FlowSessionInternal
}

interface SendRequest : SessionedFlowIORequest {
    val message: SessionMessage
}

interface ReceiveRequest<T : SessionMessage> : SessionedFlowIORequest, WaitingRequest {
    val receiveType: Class<T>
    val userReceiveType: Class<*>?
}

data class SendAndReceive<T : SessionMessage>(override val session: FlowSessionInternal,
                                              override val message: SessionMessage,
                                              override val receiveType: Class<T>,
                                              override val userReceiveType: Class<*>?) : SendRequest, ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class ReceiveOnly<T : SessionMessage>(override val session: FlowSessionInternal,
                                           override val receiveType: Class<T>,
                                           override val userReceiveType: Class<*>?) : ReceiveRequest<T> {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class SendOnly(override val session: FlowSessionInternal, override val message: SessionMessage) : SendRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class WaitForLedgerCommit(val hash: SecureHash, val fiber: FlowStateMachineImpl<*>) : WaitingRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")
