package net.corda.node.services.statemachine

import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.node.services.statemachine.FlowSessionState.Initiated
import net.corda.node.services.statemachine.FlowSessionState.Initiating
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @param retryable Indicates that the session initialisation should be retried until an expected [SessionData] response
 * is received. Note that this requires the party on the other end to be a distributed service and run an idempotent flow
 * that only sends back a single [SessionData] message before termination.
 */
// TODO rename this
class FlowSessionInternal(
        val flow: FlowLogic<*>,
        val flowSession : FlowSession,
        val ourSessionId: SessionId,
        val initiatingParty: Party?,
        var state: FlowSessionState,
        var retryable: Boolean = false) {
    val receivedMessages = ConcurrentLinkedQueue<ReceivedSessionMessage>()
    val fiber: FlowStateMachineImpl<*> get() = flow.stateMachine as FlowStateMachineImpl<*>

    override fun toString(): String {
        return "${javaClass.simpleName}(flow=$flow, ourSessionId=$ourSessionId, initiatingParty=$initiatingParty, state=$state)"
    }

    fun getPeerSessionId(): SessionId {
        val sessionState = state
        return when (sessionState) {
            is FlowSessionState.Initiated -> sessionState.peerSessionId
            else -> throw IllegalStateException("We've somehow held onto a non-initiated session: $this")
        }
    }
}

data class ReceivedSessionMessage(val peerParty: Party, val message: ExistingSessionMessage)

/**
 * [FlowSessionState] describes the session's state.
 *
 * [Uninitiated] is pre-handshake, where no communication has happened. [Initiating.otherParty] at this point holds a
 *     [Party] corresponding to either a specific peer or a service.
 * [Initiating] is pre-handshake, where the initiating message has been sent.
 * [Initiated] is post-handshake. At this point [Initiating.otherParty] will have been resolved to a specific peer
 *     [Initiated.peerParty], and the peer's sessionId has been initialised.
 */
sealed class FlowSessionState {
    abstract val sendToParty: Party

    data class Uninitiated(val otherParty: Party) : FlowSessionState() {
        override val sendToParty: Party get() = otherParty
    }

    /** [otherParty] may be a specific peer or a service party */
    data class Initiating(val otherParty: Party) : FlowSessionState() {
        override val sendToParty: Party get() = otherParty
    }

    data class Initiated(val peerParty: Party, val peerSessionId: SessionId, val context: FlowInfo) : FlowSessionState() {
        override val sendToParty: Party get() = peerParty
    }
}
