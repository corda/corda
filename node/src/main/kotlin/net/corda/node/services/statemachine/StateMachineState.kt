package net.corda.node.services.statemachine

import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.Try
import net.corda.node.services.messaging.DeduplicationHandler

/**
 * The state of the state machine, capturing the state of a flow. It consists of two parts, an *immutable* part that is
 * persisted to the database ([Checkpoint]), and the rest, which is an in-memory-only state.
 *
 * @param checkpoint the persisted part of the state.
 * @param flowLogic the [FlowLogic] associated with the flow. Note that this is mutable by the user.
 * @param pendingDeduplicationHandlers the list of incomplete deduplication handlers.
 * @param isFlowResumed true if the control is returned (or being returned) to "user-space" flow code. This is used
 *   to make [Event.DoRemainingWork] idempotent.
 * @param isTransactionTracked true if a ledger transaction has been tracked as part of a
 *   [FlowIORequest.WaitForLedgerCommit]. This used is to make tracking idempotent.
 * @param isAnyCheckpointPersisted true if at least a single checkpoint has been persisted. This is used to determine
 *   whether we should DELETE the checkpoint at the end of the flow.
 * @param isStartIdempotent true if the start of the flow is idempotent, making the skipping of the initial checkpoint
 *   possible.
 * @param isRemoved true if the flow has been removed from the state machine manager. This is used to avoid any further
 *   work.
 * @param senderUUID the identifier of the sending state machine or null if this flow is resumed from a checkpoint so that it does not participate in de-duplication high-water-marking.
 */
// TODO perhaps add a read-only environment to the state machine for things that don't change over time?
// TODO evaluate persistent datastructure libraries to replace the inefficient copying we currently do.
data class StateMachineState(
        val checkpoint: Checkpoint,
        val flowLogic: FlowLogic<*>,
        val pendingDeduplicationHandlers: List<DeduplicationHandler>,
        val isFlowResumed: Boolean,
        val isTransactionTracked: Boolean,
        val isAnyCheckpointPersisted: Boolean,
        val isStartIdempotent: Boolean,
        val isRemoved: Boolean,
        val senderUUID: String?
)

/**
 * @param invocationContext the initiator of the flow.
 * @param ourIdentity the identity the flow is run as.
 * @param sessions map of source session ID to session state.
 * @param subFlowStack the stack of currently executing subflows.
 * @param flowState the state of the flow itself, including the frozen fiber/FlowLogic.
 * @param errorState the "dirtiness" state including the involved errors and their propagation status.
 * @param numberOfSuspends the number of flow suspends due to IO API calls.
 */
data class Checkpoint(
        val invocationContext: InvocationContext,
        val ourIdentity: Party,
        val sessions: SessionMap, // This must preserve the insertion order!
        val subFlowStack: List<SubFlow>,
        val flowState: FlowState,
        val errorState: ErrorState,
        val numberOfSuspends: Int
) {
    companion object {

        fun create(
                invocationContext: InvocationContext,
                flowStart: FlowStart,
                flowLogicClass: Class<FlowLogic<*>>,
                frozenFlowLogic: SerializedBytes<FlowLogic<*>>,
                ourIdentity: Party,
                deduplicationSeed: String,
                subFlowVersion: SubFlowVersion
        ): Try<Checkpoint> {
            return SubFlow.create(flowLogicClass, subFlowVersion).map { topLevelSubFlow ->
                Checkpoint(
                        invocationContext = invocationContext,
                        ourIdentity = ourIdentity,
                        sessions = emptyMap(),
                        subFlowStack = listOf(topLevelSubFlow),
                        flowState = FlowState.Unstarted(flowStart, frozenFlowLogic),
                        errorState = ErrorState.Clean,
                        numberOfSuspends = 0
                )
            }
        }
    }
}

/**
 * The state of a session.
 */
sealed class SessionState {

    abstract val deduplicationSeed: String

    /**
     * We haven't yet sent the initialisation message
     */
    data class Uninitiated(
            val party: Party,
            val initiatingSubFlow: SubFlow.Initiating,
            val sourceSessionId: SessionId,
            val additionalEntropy: Long
    ) : SessionState() {
        override val deduplicationSeed: String get() = "R-${sourceSessionId.toLong}-$additionalEntropy"
    }

    /**
     * We have sent the initialisation message but have not yet received a confirmation.
     * @property rejectionError if non-null the initiation failed.
     */
    data class Initiating(
            val bufferedMessages: List<Pair<DeduplicationId, ExistingSessionMessagePayload>>,
            val rejectionError: FlowError?,
            override val deduplicationSeed: String
    ) : SessionState()

    /**
     * We have received a confirmation, the peer party and session id is resolved.
     * @property errors if not empty the session is in an errored state.
     */
    data class Initiated(
            val peerParty: Party,
            val peerFlowInfo: FlowInfo,
            val receivedMessages: List<DataSessionMessage>,
            val initiatedState: InitiatedSessionState,
            val errors: List<FlowError>,
            override val deduplicationSeed: String
    ) : SessionState()
}

typealias SessionMap = Map<SessionId, SessionState>

/**
 * Tracks whether an initiated session state is live or has ended. This is a separate state, as we still need the rest
 * of [SessionState.Initiated], even when the session has ended, for un-drained session messages and potential future
 * [FlowInfo] requests.
 */
sealed class InitiatedSessionState {
    data class Live(val peerSinkSessionId: SessionId) : InitiatedSessionState()
    object Ended : InitiatedSessionState() { override fun toString() = "Ended" }
}

/**
 * Represents the way the flow has started.
 */
sealed class FlowStart {
    /**
     * The flow was started explicitly e.g. through RPC or a scheduled state.
     */
    object Explicit : FlowStart() { override fun toString() = "Explicit" }

    /**
     * The flow was started implicitly as part of session initiation.
     */
    data class Initiated(
            val peerSession: FlowSessionImpl,
            val initiatedSessionId: SessionId,
            val initiatingMessage: InitialSessionMessage,
            val senderCoreFlowVersion: Int?,
            val initiatedFlowInfo: FlowInfo
    ) : FlowStart() { override fun toString() = "Initiated" }
}

/**
 * Represents the user-space related state of the flow.
 */
sealed class FlowState {

    /**
     * The flow's unstarted state. We should always be able to start a fresh flow fiber from this datastructure.
     *
     * @param flowStart How the flow was started.
     * @param frozenFlowLogic The serialized user-provided [FlowLogic].
     */
    data class Unstarted(
            val flowStart: FlowStart,
            val frozenFlowLogic: SerializedBytes<FlowLogic<*>>
    ) : FlowState() {
        override fun toString() = "Unstarted(flowStart=$flowStart, frozenFlowLogic=${frozenFlowLogic.hash})"
    }

    /**
     * The flow's started state, this means the user-code has suspended on an IO request.
     *
     * @param flowIORequest what IO request the flow has suspended on.
     * @param frozenFiber the serialized fiber itself.
     */
    data class Started(
            val flowIORequest: FlowIORequest<*>,
            val frozenFiber: SerializedBytes<FlowStateMachineImpl<*>>
    ) : FlowState() {
        override fun toString() = "Started(flowIORequest=$flowIORequest, frozenFiber=${frozenFiber.hash})"
    }
}

/**
 * @param errorId the ID of the error. This is generated once for the source error and is propagated to neighbour
 *   sessions.
 * @param exception the exception itself. Note that this may not contain information about the source error depending
 *   on whether the source error was a FlowException or otherwise.
 */
data class FlowError(val errorId: Long, val exception: Throwable)

/**
 * The flow's error state.
 */
sealed class ErrorState {
    abstract fun addErrors(newErrors: List<FlowError>): ErrorState

    /**
     * The flow is in a clean state.
     */
    object Clean : ErrorState() {
        override fun addErrors(newErrors: List<FlowError>): ErrorState {
            return Errored(newErrors, 0, false)
        }
        override fun toString() = "Clean"
    }

    /**
     * The flow has dirtied because of an uncaught exception from user code or other error condition during a state
     * transition.
     * @param errors the list of errors. Multiple errors may be associated with the errored flow e.g. when multiple
     *   sessions are errored and have been waited on.
     * @param propagatedIndex the index of the first error that hasn't yet been propagated.
     * @param propagating true if error propagation was triggered. If this is set the dirtiness is permanent as the
     *   sessions associated with the flow have been (or about to be) dirtied in counter-flows.
     */
    data class Errored(
            val errors: List<FlowError>,
            val propagatedIndex: Int,
            val propagating: Boolean
    ) : ErrorState() {
        override fun addErrors(newErrors: List<FlowError>): ErrorState {
            return copy(errors = errors + newErrors)
        }
    }
}

/**
 * Stored per [SubFlow]. Contains metadata around the version of the code at the Checkpointing moment.
 */
sealed class SubFlowVersion {
    abstract val platformVersion: Int
    data class CoreFlow(override val platformVersion: Int) : SubFlowVersion()
    data class CorDappFlow(override val platformVersion: Int, val corDappName: String, val corDappHash: SecureHash) : SubFlowVersion()
}