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
import net.corda.node.services.messaging.MessageIdentifier
import net.corda.node.services.messaging.SenderSequenceNumber
import net.corda.node.services.messaging.SenderUUID
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.security.Principal
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import kotlin.math.max

/**
 * The state of the state machine, capturing the state of a flow. It consists of two parts, an *immutable* part that is
 * persisted to the database ([Checkpoint]), and the rest, which is an in-memory-only state.
 *
 * @param checkpoint the persisted part of the state.
 * @param flowLogic the [FlowLogic] associated with the flow. Note that this is mutable by the user.
 * @param pendingDeduplicationHandlers the list of incomplete deduplication handlers.
 * @param closedSessionsPendingToBeSignalled the sessions that have been closed and need to be signalled to the messaging layer on the next checkpoint (along with some metadata).
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
 * @param numberOfCommits The number of times the flow's checkpoint has been successfully committed. This field is a var so that it can be
 * updated after committing a database transaction that contained a checkpoint insert/update.
 * @param lock The flow's lock, used to prevent the flow performing a transition while being interacted with from external threads, and
 * vise-versa.
 */
// TODO perhaps add a read-only environment to the state machine for things that don't change over time?
// TODO evaluate persistent datastructure libraries to replace the inefficient copying we currently do.
data class StateMachineState(
    val checkpoint: Checkpoint,
    val flowLogic: FlowLogic<*>,
    val pendingDeduplicationHandlers: List<DeduplicationHandler>,
    val closedSessionsPendingToBeSignalled: Map<SessionId, Pair<SenderUUID?, SenderSequenceNumber?>>,
    val isFlowResumed: Boolean,
    val isWaitingForFuture: Boolean,
    var future: Future<*>?,
    val isAnyCheckpointPersisted: Boolean,
    val isStartIdempotent: Boolean,
    val isRemoved: Boolean,
    val isKilled: Boolean,
    val senderUUID: String?,
    val reloadCheckpointAfterSuspendCount: Int?,
    var numberOfCommits: Int,
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
                isEnabledTimedFlow: Boolean,
                timestamp: Instant
        ): Try<Checkpoint> {
            return SubFlow.create(flowLogicClass, subFlowVersion, isEnabledTimedFlow).map { topLevelSubFlow ->
                Checkpoint(
                    checkpointState = CheckpointState(
                        invocationContext,
                        ourIdentity,
                        emptyMap(),
                        emptySet(),
                        listOf(topLevelSubFlow),
                        numberOfSuspends = 0,
                        // We set this to 1 here to avoid an extra copy and increment in UnstartedFlowTransition.createInitialCheckpoint
                        numberOfCommits = 1,
                        suspensionTime = timestamp
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
<<<<<<< HEAD
 * @param invocationContext The initiator of the flow.
 * @param ourIdentity The identity the flow is run as.
 * @param sessions Map of source session ID to session state.
 * @param sessionsToBeClosed The sessions that have pending session end messages and need to be closed. This is available to avoid scanning all the sessions.
 * @param subFlowStack The stack of currently executing subflows.
 * @param numberOfSuspends The number of flow suspends due to IO API calls.
 * @param numberOfCommits The number of times this checkpoint has been persisted.
 * @param suspensionTime the time of the last suspension. This is supposed to be used as a stable timestamp in case of replays.
 */
@CordaSerializable
data class CheckpointState(
    val invocationContext: InvocationContext,
    val ourIdentity: Party,
    val sessions: SessionMap, // This must preserve the insertion order!
    val sessionsToBeClosed: Set<SessionId>,
    val subFlowStack: List<SubFlow>,
    val numberOfSuspends: Int,
    val numberOfCommits: Int,
    val suspensionTime: Instant
)

/**
 * The state of a session.
 */
sealed class SessionState {

    abstract val deduplicationSeed: String

    /**
     * the sender UUID last seen in this session, if there was one.
     */
    abstract val lastSenderUUID: SenderUUID?

    /**
     * the sender sequence number last seen in this session, if there was one.
     */
    abstract val lastSenderSeqNo: SenderSequenceNumber?

    /**
     * the messages that have been received and are pending processing indexed by their sequence number.
     * this could be any [ExistingSessionMessagePayload] type in theory, but it in practice it can only be one of the following types now:
     *   * [DataSessionMessage]
     *   * [ErrorSessionMessage]
     *   * [EndSessionMessage]
     */
    abstract val receivedMessages: Map<Int, ExistingSessionMessagePayload>

    /**
     * Returns a new session state with the specified messages added to the list of received messages.
     */
    fun addReceivedMessages(message: ExistingSessionMessagePayload, messageIdentifier: MessageIdentifier, senderUUID: String?, senderSequenceNumber: Long?): SessionState {
        val newReceivedMessages = receivedMessages.plus(messageIdentifier.sessionSequenceNumber to message)
        val (newLastSenderUUID, newLastSenderSeqNo) = calculateSenderInfo(lastSenderUUID, lastSenderSeqNo, senderUUID, senderSequenceNumber)
        return when(this) {
            is Uninitiated -> { copy(receivedMessages = newReceivedMessages, lastSenderUUID = newLastSenderUUID, lastSenderSeqNo = newLastSenderSeqNo) }
            is Initiating -> { copy(receivedMessages = newReceivedMessages, lastSenderUUID = newLastSenderUUID, lastSenderSeqNo = newLastSenderSeqNo) }
            is Initiated -> { copy(receivedMessages = newReceivedMessages, lastSenderUUID = newLastSenderUUID, lastSenderSeqNo = newLastSenderSeqNo) }
        }
    }

    private fun calculateSenderInfo(currentSender: String?, currentSenderSeqNo: Long?, msgSender: String?, msgSenderSeqNo: Long?): Pair<String?, Long?> {
        return if (msgSender != null && msgSenderSeqNo != null) {
            if (currentSenderSeqNo != null)
                Pair(msgSender, max(msgSenderSeqNo, currentSenderSeqNo))
            else
                Pair(msgSender, msgSenderSeqNo)
        } else {
            Pair(currentSender, currentSenderSeqNo)
        }
    }

    /**
     * We haven't yet sent the initialisation message.
     * This really means that the flow is in a state before sending the initialisation message,
     * but in reality it could have sent it before and fail before reaching the next checkpoint, thus ending up replaying from the last checkpoint.
     *
     * @param hasBeenAcknowledged whether a positive response to a session initiation has already been received and the associated confirmation message, if so.
     * @param hasBeenRejected whether a negative response to a session initiation has already been received and the associated rejection message, if so.
     */
    data class Uninitiated(
            val destination: Destination,
            val initiatingSubFlow: SubFlow.Initiating,
            val sourceSessionId: SessionId,
            val additionalEntropy: Long,
            val hasBeenAcknowledged: Pair<Party, ConfirmSessionMessage>?,
            val hasBeenRejected: RejectSessionMessage?,
            override val receivedMessages: Map<Int, ExistingSessionMessagePayload>,
            override val lastSenderUUID: String?,
            override val lastSenderSeqNo: Long?
    ) : SessionState() {
        override val deduplicationSeed: String get() = "R-${sourceSessionId.value}-$additionalEntropy"
    }

    /**
     * We have sent the initialisation message but have not yet received a confirmation.
     * @property bufferedMessages the messages that have been buffered to be sent after the session is confirmed from the other side.
     * @property rejectionError if non-null the initiation failed.
     * @property nextSendingSeqNumber the sequence number of the next message to be sent.
     * @property shardId the shard ID of the associated flow to be embedded on all the messages sent from this session.
     */
    data class Initiating(
            val bufferedMessages: List<Pair<MessageIdentifier, ExistingSessionMessagePayload>>,
            val rejectionError: FlowError?,
            override val deduplicationSeed: String,
            val nextSendingSeqNumber: Int,
            val shardId: String,
            override val receivedMessages: Map<Int, ExistingSessionMessagePayload>,
            override val lastSenderUUID: String?,
            override val lastSenderSeqNo: Long?
    ) : SessionState() {

        /**
         * Buffers an outgoing message to be sent when ready.
         * Returns the new form of the state
         */
        fun bufferMessage(messageIdentifier: MessageIdentifier, messagePayload: ExistingSessionMessagePayload): SessionState {
            return this.copy(bufferedMessages = bufferedMessages + Pair(messageIdentifier, messagePayload), nextSendingSeqNumber = nextSendingSeqNumber + 1)
        }

        /**
         * A batched form of [bufferMessage].
         */
        fun bufferMessages(messages: List<Pair<MessageIdentifier, ExistingSessionMessagePayload>>): SessionState {
            return this.copy(bufferedMessages = bufferedMessages + messages, nextSendingSeqNumber = nextSendingSeqNumber + messages.size)
        }
    }

    /**
     * We have received a confirmation, the peer party and session id is resolved.
     * @property otherSideErrored whether the session has received an error from the other side.
     * @property nextSendingSeqNumber the sequence number that corresponds to the next message to be sent.
     * @property lastProcessedSeqNumber the sequence number of the last message that has been processed.
     * @property shardId the shard ID of the associated flow to be embedded on all the messages sent from this session.
     */
    data class Initiated(
            val peerParty: Party,
            val peerFlowInfo: FlowInfo,
            val otherSideErrored: Boolean,
            val peerSinkSessionId: SessionId,
            override val deduplicationSeed: String,
            val nextSendingSeqNumber: Int,
            val lastProcessedSeqNumber: Int,
            val shardId: String,
            override val receivedMessages: Map<Int, ExistingSessionMessagePayload>,
            override val lastSenderUUID: String?,
            override val lastSenderSeqNo: Long?
    ) : SessionState() {

        /**
         * Indicates whether this message has already been processed.
         */
        fun isDuplicate(messageIdentifier: MessageIdentifier): Boolean {
            return messageIdentifier.sessionSequenceNumber <= lastProcessedSeqNumber
        }

        /**
         * Indicates whether the session has an error message pending from the other side.
         */
        fun hasErrored(): Boolean {
            return hasNextMessageArrived() && receivedMessages[lastProcessedSeqNumber + 1] is ErrorSessionMessage
        }

        /**
         * Indicates whether the next expected message has arrived.
         */
        fun hasNextMessageArrived(): Boolean {
            return receivedMessages.containsKey(lastProcessedSeqNumber + 1)
        }

        /**
         * Returns the next message to be processed and the new session state.
         * If you want to check first whether the next message has arrived, call [hasNextMessageArrived]
         *
         * @throws [IllegalArgumentException] if the next hasn't arrived.
         */
        fun extractMessage(): Pair<ExistingSessionMessagePayload, Initiated> {
            if (!hasNextMessageArrived()) {
                throw IllegalArgumentException("Tried to extract a message that hasn't arrived yet.")
            }

            val message = receivedMessages[lastProcessedSeqNumber + 1]!!
            val newState = this.copy(receivedMessages = receivedMessages.minus(lastProcessedSeqNumber + 1), lastProcessedSeqNumber = lastProcessedSeqNumber + 1)
            return Pair(message, newState)
        }
    }
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
            val initiatedFlowInfo: FlowInfo,
            val shardIdentifier: String,
            val senderUUID: String?,
            val senderSequenceNumber: Long?
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

sealed class FlowWithClientIdStatus(val flowId: StateMachineRunId, val user: Principal) {

    fun isPermitted(user: Principal): Boolean = user.name == this.user.name

    class Active(
        flowId: StateMachineRunId,
        user: Principal,
        val flowStateMachineFuture: CordaFuture<out FlowStateMachineHandle<out Any?>>
    ) : FlowWithClientIdStatus(flowId, user)

    class Removed(flowId: StateMachineRunId, user: Principal, val succeeded: Boolean) : FlowWithClientIdStatus(flowId, user)
}

data class FlowResultMetadata(
    val status: Checkpoint.FlowStatus,
    val clientId: String?,
    val user: Principal
)