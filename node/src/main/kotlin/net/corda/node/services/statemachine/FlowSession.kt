package net.corda.node.services.statemachine

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.node.services.statemachine.FlowSessionState.Initiated
import net.corda.node.services.statemachine.FlowSessionState.Initiating
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @param retryable Indicates that the session initialisation should be retried until an expected [SessionData] response
 * is received. Note that this requires the party on the other end to be a distributed service and run an idempotent flow
 * that only sends back a single [SessionData] message before termination.
 */
class FlowSession(
        val flow: FlowLogic<*>,
        val ourSessionId: Long,
        val initiatingParty: Party?,
        var state: FlowSessionState,
        val retryable: Boolean = false) {
    val receivedMessages = ConcurrentLinkedQueue<ReceivedSessionMessage<*>>()
    val fiber: FlowStateMachineImpl<*> get() = flow.stateMachine as FlowStateMachineImpl<*>

    override fun toString(): String {
        return "${javaClass.simpleName}(flow=$flow, ourSessionId=$ourSessionId, initiatingParty=$initiatingParty, state=$state)"
    }
}

/**
 * [FlowSessionState] describes the session's state.
 *
 * [Initiating] is pre-handshake. [Initiating.otherParty] at this point holds a [Party] corresponding to either a
 *     specific peer or a service.
 * [Initiated] is post-handshake. At this point [Initiating.otherParty] will have been resolved to a specific peer
 *     [Initiated.peerParty], and the peer's sessionId has been initialised.
 */
sealed class FlowSessionState {
    abstract val sendToParty: Party

    /** [otherParty] may be a specific peer or a service party */
    data class Initiating(val otherParty: Party) : FlowSessionState() {
        override val sendToParty: Party get() = otherParty
    }

    data class Initiated(val peerParty: Party, val peerSessionId: Long) : FlowSessionState() {
        override val sendToParty: Party get() = peerParty
    }
}
