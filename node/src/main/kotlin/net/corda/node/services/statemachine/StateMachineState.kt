package net.corda.node.services.statemachine

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.Destination
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.utilities.Try
import net.corda.node.services.messaging.DeduplicationHandler
import java.lang.IllegalStateException
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.Semaphore

/**
 * The state of the state machine, capturing the state of a flow. It consists of two parts, an *immutable* part that is
 * persisted to the database ([Checkpoint]), and the rest, which is an in-memory-only state.
 *
 * @param checkpoint the persisted part of the state.
 * @param flowLogic the [FlowLogic] associated with the flow. Note that this is mutable by the user.
 * @param pendingDeduplicationHandlers the list of incomplete deduplication handlers.
 * @param isFlowResumed true if the control is returned (or being returned) to "user-space" flow code. This is used
 *   to make [Event.DoRemainingWork] idempotent.
 * @param isWaitingForFuture true if the flow is waiting for the completion of a future triggered by one of the statemachine's actions
 * @param future If the flow is relying on a [Future] completing, then this field will be set otherwise it remains null
 * @param isAnyCheckpointPersisted true if at least a single checkpoint has been persisted. This is used to determine
 *   whether we should DELETE the checkpoint at the end of the flow.
 * @param isStartIdempotent true if the start of the flow is idempotent, making the skipping of the initial checkpoint
 *   possible.
 * @param isRemoved true if the flow has been removed from the state machine manager. This is used to avoid any further
 *   work.
 * @param isKilled true if the flow has been marked as killed. This is used to cause a flow to move to a killed flow transition no matter
 * what event it is set to process next.
 * @param senderUUID the identifier of the sending state machine or null if this flow is resumed from a checkpoint so that it does not participate in de-duplication high-water-marking.
 * @param reloadCheckpointAfterSuspendCount The number of times a flow has been reloaded (not retried). This is [null] when
 * [NodeConfiguration.reloadCheckpointAfterSuspendCount] is not enabled.
 * @param lock The flow's lock, used to prevent the flow performing a transition while being interacted with from external threads, and
 * vise-versa.
 */
// TODO perhaps add a read-only environment to the state machine for things that don't change over time?
// TODO evaluate persistent datastructure libraries to replace the inefficient copying we currently do.
data class StateMachineState(
    val checkpoint: Checkpoint,
    val flowLogic: FlowLogic<*>,
    val pendingDeduplicationHandlers: List<DeduplicationHandler>,
    val isFlowResumed: Boolean,
    val isWaitingForFuture: Boolean,
    var future: Future<*>?,
    val isAnyCheckpointPersisted: Boolean,
    val isStartIdempotent: Boolean,
    val isRemoved: Boolean,
    val isKilled: Boolean,
    val senderUUID: String?,
    val reloadCheckpointAfterSuspendCount: Int?,
    val lock: Semaphore
) : KryoSerializable {
    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${StateMachineState::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${StateMachineState::class.qualifiedName} should never be deserialized")
    }
}

/**
 * @param checkpointState the state of the checkpoint
 * @param flowState the state of the flow itself, including the frozen fiber/FlowLogic.
 * @param errorState the "dirtiness" state including the involved errors and their propagation status.
 */
data class Checkpoint(
        val checkpointState: CheckpointState,
        val flowState: FlowState,
        val errorState: ErrorState,
        val result: Any? = null,
        val status: FlowStatus = FlowStatus.RUNNABLE,
        val progressStep: String? = null,
        val flowIoRequest: String? = null,
        val compatible: Boolean = true
) {
    @CordaSerializable
    enum class FlowStatus {
        RUNNABLE,
        FAILED,
        COMPLETED,
        HOSPITALIZED,
        KILLED,
        PAUSED
    }

    /**
     * [timestamp] will get updated every time a [Checkpoint] object is created/ created by copy.
     * It will be updated, therefore, for example when a flow is being suspended or whenever a flow
     * is being loaded from [Checkpoint] through [Serialized.deserialize].
     */
    val timestamp: Instant = Instant.now()

    companion object {

        fun create(
                invocationContext: InvocationContext,
                flowStart: FlowStart,
                flowLogicClass: Class<FlowLogic<*>>,
                frozenFlowLogic: SerializedBytes<FlowLogic<*>>,
                ourIdentity: Party,
                subFlowVersion: SubFlowVersion,
                isEnabledTimedFlow: Boolean
        ): Try<Checkpoint> {
            return SubFlow.create(flowLogicClass, subFlowVersion, isEnabledTimedFlow).map { topLevelSubFlow ->
                Checkpoint(
                    checkpointState = CheckpointState(
                        invocationContext,
                        ourIdentity,
                        emptyMap(),
                        emptySet(),
                        listOf(topLevelSubFlow),
                        numberOfSuspends = 0
                    ),
                    flowState = FlowState.Unstarted(flowStart, frozenFlowLogic),
                    errorState = ErrorState.Clean
                )
            }
        }
    }

    /**
     * Returns a copy of the Checkpoint with a new session map.
     * @param sessions the new map of session ID to session state.
     */
    fun setSessions(sessions: SessionMap) : Checkpoint {
        return copy(checkpointState = checkpointState.copy(sessions = sessions))
    }

    /**
     * Returns a copy of the Checkpoint with an extra session added to the session map.
     * @param session the extra session to add.
     */
    fun addSession(session: Pair<SessionId, SessionState>) : Checkpoint {
        return copy(checkpointState = checkpointState.copy(sessions = checkpointState.sessions + session))
    }

    fun addSessionsToBeClosed(sessionIds: Set<SessionId>): Checkpoint {
        return copy(checkpointState = checkpointState.copy(sessionsToBeClosed = checkpointState.sessionsToBeClosed + sessionIds))
    }

    /**
     * Returns a copy of the Checkpoint with the specified session removed from the session map.
     * @param sessionIds the sessions to remove.
     */
    fun removeSessions(sessionIds: Set<SessionId>): Checkpoint {
        return copy(
            checkpointState = checkpointState.copy(
                sessions = checkpointState.sessions - sessionIds,
                sessionsToBeClosed = checkpointState.sessionsToBeClosed - sessionIds
            )
        )
    }

    /**
     * Returns a copy of the Checkpoint with a new subFlow stack.
     * @param subFlows the new List of subFlows.
     */
    fun setSubflows(subFlows: List<SubFlow>) : Checkpoint {
        return copy(checkpointState = checkpointState.copy(subFlowStack = subFlows))
    }

    /**
     * Returns a copy of the Checkpoint with an extra subflow added to the subFlow Stack.
     * @param subFlow the subFlow to add to the stack of subFlows
     */
    fun addSubflow(subFlow: SubFlow) : Checkpoint {
        return copy(checkpointState = checkpointState.copy(subFlowStack = checkpointState.subFlowStack + subFlow))
    }

    /**
     * A partially serialized form of [Checkpoint].
     *
     * [Checkpoint.Serialized] contains the same fields as [Checkpoint] except that some of its fields are still serialized. The checkpoint
     * can then be deserialized as needed.
     */
    data class Serialized(
        val serializedCheckpointState: SerializedBytes<CheckpointState>,
        val serializedFlowState: SerializedBytes<FlowState>?,
        val errorState: ErrorState,
        val result: SerializedBytes<Any>?,
        val status: FlowStatus,
        val progressStep: String?,
        val flowIoRequest: String?,
        val compatible: Boolean
    ) {
        /**
         * Deserializes the serialized fields contained in [Checkpoint.Serialized].
         *
         * @return A [Checkpoint] with all its fields filled in from [Checkpoint.Serialized]
         */
        fun deserialize(checkpointSerializationContext: CheckpointSerializationContext): Checkpoint {
            val flowState = when(status) {
                FlowStatus.PAUSED -> FlowState.Paused
                FlowStatus.COMPLETED, FlowStatus.FAILED -> FlowState.Finished
                else -> serializedFlowState!!.checkpointDeserialize(checkpointSerializationContext)
            }
            return Checkpoint(
                checkpointState = serializedCheckpointState.checkpointDeserialize(checkpointSerializationContext),
                flowState = flowState,
                errorState = errorState,
                result = result?.deserialize(context = SerializationDefaults.STORAGE_CONTEXT),
                status = status,
                progressStep = progressStep,
                flowIoRequest = flowIoRequest,
                compatible = compatible
            )
        }
    }
}

/**
 * @param invocationContext the initiator of the flow.
 * @param ourIdentity the identity the flow is run as.
 * @param sessions map of source session ID to session state.
 * @param sessionsToBeClosed the sessions that have pending session end messages and need to be closed. This is available to avoid scanning all the sessions.
 * @param subFlowStack the stack of currently executing subflows.
 * @param numberOfSuspends the number of flow suspends due to IO API calls.
 */
@CordaSerializable
data class CheckpointState(
        val invocationContext: InvocationContext,
        val ourIdentity: Party,
        val sessions: SessionMap, // This must preserve the insertion order!
        val sessionsToBeClosed: Set<SessionId>,
        val subFlowStack: List<SubFlow>,
        val numberOfSuspends: Int
)

/**
 * The state of a session.
 */
sealed class SessionState {

    abstract val deduplicationSeed: String

    /**
     * We haven't yet sent the initialisation message
     */
    data class Uninitiated(
            val destination: Destination,
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
     * @property receivedMessages the messages that have been received and are pending processing.
     *   this could be any [ExistingSessionMessagePayload] type in theory, but it in practice it can only be one of the following types now:
     *   * [DataSessionMessage]
     *   * [ErrorSessionMessage]
     *   * [EndSessionMessage]
     * @property otherSideErrored whether the session has received an error from the other side.
     */
    data class Initiated(
            val peerParty: Party,
            val peerFlowInfo: FlowInfo,
            val receivedMessages: List<ExistingSessionMessagePayload>,
            val otherSideErrored: Boolean,
            val peerSinkSessionId: SessionId,
            override val deduplicationSeed: String
    ) : SessionState()
}

typealias SessionMap = Map<SessionId, SessionState>

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

    /**
     * The flow is paused. To save memory we don't store the FlowState
     */
    object Paused: FlowState()

    /**
     * The flow has finished. It does not have a running fiber that needs to be serialized and checkpointed.
     */
    object Finished : FlowState()

}

/**
 * @param errorId the ID of the error. This is generated once for the source error and is propagated to neighbour
 *   sessions.
 * @param exception the exception itself. Note that this may not contain information about the source error depending
 *   on whether the source error was a FlowException or otherwise.
 */
@CordaSerializable
data class FlowError(val errorId: Long, val exception: Throwable)

/**
 * The flow's error state.
 */
@CordaSerializable
sealed class ErrorState {
    abstract fun addErrors(newErrors: List<FlowError>): ErrorState

    /**
     * The flow is in a clean state.
     */
    @CordaSerializable
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
    @CordaSerializable
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

sealed class FlowWithClientIdStatus(val flowId: StateMachineRunId) {
    class Active(
        flowId: StateMachineRunId,
        val flowStateMachineFuture: CordaFuture<out FlowStateMachineHandle<out Any?>>
    ) : FlowWithClientIdStatus(flowId)

    class Removed(flowId: StateMachineRunId, val succeeded: Boolean) : FlowWithClientIdStatus(flowId)
}

data class FlowResultMetadata(
    val status: Checkpoint.FlowStatus,
    val clientId: String?
)