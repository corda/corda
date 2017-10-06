package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import java.time.Instant

interface FlowIORequest {
    // This is used to identify where we suspended, in case of message mismatch errors and other things where we
    // don't have the original stack trace because it's in a suspended fiber.
    val stackTraceInCaseOfProblems: StackSnapshot
}

interface WaitingRequest : FlowIORequest {
    fun shouldResume(message: ExistingSessionMessage, session: FlowSessionInternal): Boolean
}

interface SessionedFlowIORequest : FlowIORequest {
    val session: FlowSessionInternal
}

interface SendRequest : SessionedFlowIORequest {
    val message: SessionMessage
}

interface ReceiveRequest<T : SessionMessage> : SessionedFlowIORequest, WaitingRequest {
    val receiveType: Class<T>
    val userReceiveType: Class<*>?

    override fun shouldResume(message: ExistingSessionMessage, session: FlowSessionInternal): Boolean = this.session === session
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

class ReceiveAll(val requests: List<ReceiveRequest<SessionData>>) : WaitingRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()

    private fun isComplete(received: LinkedHashMap<FlowSessionInternal, RequestMessage>): Boolean {
        return received.keys == requests.map { it.session }.toSet()
    }
    private fun shouldResumeIfRelevant() = requests.all { hasSuccessfulEndMessage(it) }

    private fun hasSuccessfulEndMessage(it: ReceiveRequest<SessionData>): Boolean {
        return it.session.receivedMessages.map { it.message }.any { it is SessionData || it is SessionEnd }
    }

    @Suspendable
    fun suspendAndExpectReceive(suspend: Suspend): Map<FlowSessionInternal, RequestMessage> {
        val receivedMessages = LinkedHashMap<FlowSessionInternal, RequestMessage>()

        poll(receivedMessages)
        return if (isComplete(receivedMessages)) {
            receivedMessages
        } else {
            suspend(this)
            poll(receivedMessages)
            if (isComplete(receivedMessages)) {
                receivedMessages
            } else {
                throw IllegalStateException(requests.filter { it.session !in receivedMessages.keys }.map { "Was expecting a ${it.receiveType.simpleName} but instead got nothing for $it." }.joinToString { "\n" })
            }
        }
    }

    interface Suspend {
        @Suspendable
        operator fun invoke(request: FlowIORequest)
    }

    @Suspendable
    private fun poll(receivedMessages: LinkedHashMap<FlowSessionInternal, RequestMessage>) {
        return requests.filter { it.session !in receivedMessages.keys }.forEach { request ->
            poll(request)?.let {
                receivedMessages[request.session] = RequestMessage(request, it)
            }
        }
    }

    @Suspendable
    private fun poll(request: ReceiveRequest<SessionData>): ReceivedSessionMessage<*>? {
        return request.session.receivedMessages.poll()
    }

    override fun shouldResume(message: ExistingSessionMessage, session: FlowSessionInternal): Boolean = isRelevant(session) && shouldResumeIfRelevant()

    private fun isRelevant(session: FlowSessionInternal) = requests.any { it.session === session }

    data class RequestMessage(val request: ReceiveRequest<SessionData>, val message: ReceivedSessionMessage<*>)
}

data class SendOnly(override val session: FlowSessionInternal, override val message: SessionMessage) : SendRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

data class WaitForLedgerCommit(val hash: SecureHash, val fiber: FlowStateMachineImpl<*>) : WaitingRequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()

    override fun shouldResume(message: ExistingSessionMessage, session: FlowSessionInternal): Boolean = message is ErrorSessionEnd
}

data class Sleep(val until: Instant, val fiber: FlowStateMachineImpl<*>) : FlowIORequest {
    @Transient
    override val stackTraceInCaseOfProblems: StackSnapshot = StackSnapshot()
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")
