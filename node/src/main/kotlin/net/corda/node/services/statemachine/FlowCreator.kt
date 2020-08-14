package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.channels.Channels
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.transitions.StateMachine
import net.corda.node.utilities.isEnabledTimedFlow
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.apache.activemq.artemis.utils.ReusableLatch
import java.security.SecureRandom
import java.util.concurrent.Semaphore

class Flow<A>(val fiber: FlowStateMachineImpl<A>, val resultFuture: OpenFuture<Any?>)

data class NonResidentFlow(
    val runId: StateMachineRunId,
    var checkpoint: Checkpoint,
    val resultFuture: OpenFuture<Any?> = openFuture(),
    val resumable: Boolean = true,
    val hospitalized: Boolean = false
) {
    val events = mutableListOf<ExternalEvent>()

    fun addExternalEvent(message: ExternalEvent) {
        events.add(message)
    }
}

class FlowCreator(
    private val checkpointSerializationContext: CheckpointSerializationContext,
    private val checkpointStorage: CheckpointStorage,
    private val scheduler: FiberScheduler,
    private val database: CordaPersistence,
    private val transitionExecutor: TransitionExecutor,
    private val actionExecutor: ActionExecutor,
    private val secureRandom: SecureRandom,
    private val serviceHub: ServiceHubInternal,
    private val unfinishedFibers: ReusableLatch,
    private val resetCustomTimeout: (StateMachineRunId, Long) -> Unit) {

    companion object {
        private val logger = contextLogger()
    }

    private val reloadCheckpointAfterSuspend = serviceHub.configuration.reloadCheckpointAfterSuspend

    fun createFlowFromNonResidentFlow(nonResidentFlow: NonResidentFlow): Flow<*>? {
        // As for paused flows we don't extract the serialized flow state we need to re-extract the checkpoint from the database.
        val checkpoint = when (nonResidentFlow.checkpoint.status) {
            Checkpoint.FlowStatus.PAUSED -> {
                val serialized = database.transaction {
                    checkpointStorage.getCheckpoint(nonResidentFlow.runId)
                }
                serialized?.copy(status = Checkpoint.FlowStatus.RUNNABLE)?.deserialize(checkpointSerializationContext) ?: return null
            }
            else -> nonResidentFlow.checkpoint
        }
        return createFlowFromCheckpoint(nonResidentFlow.runId, checkpoint, resultFuture = nonResidentFlow.resultFuture)
    }

    @Suppress("LongParameterList")
    fun createFlowFromCheckpoint(
        runId: StateMachineRunId,
        oldCheckpoint: Checkpoint,
        reloadCheckpointAfterSuspendCount: Int? = null,
        lock: Semaphore = Semaphore(1),
        resultFuture: OpenFuture<Any?> = openFuture(),
        firstRestore: Boolean = true
    ): Flow<*>? {
        val fiber = oldCheckpoint.getFiberFromCheckpoint(runId, firstRestore)
        var checkpoint = oldCheckpoint
        if (fiber == null) {
            updateCompatibleInDb(runId, false)
            return null
        } else if (!oldCheckpoint.compatible) {
            updateCompatibleInDb(runId, true)
            checkpoint = checkpoint.copy(compatible = true)
        }
        checkpoint = checkpoint.copy(status = Checkpoint.FlowStatus.RUNNABLE)

        fiber.logic.stateMachine = fiber
        verifyFlowLogicIsSuspendable(fiber.logic)
        fiber.transientValues = createTransientValues(runId, resultFuture)
        fiber.transientState = createStateMachineState(
            checkpoint = checkpoint,
            fiber = fiber,
            anyCheckpointPersisted = true,
            reloadCheckpointAfterSuspendCount = reloadCheckpointAfterSuspendCount
                ?: if (reloadCheckpointAfterSuspend) checkpoint.checkpointState.numberOfSuspends else null,
            lock = lock
        )
        return Flow(fiber, resultFuture)
    }

    private fun updateCompatibleInDb(runId: StateMachineRunId, compatible: Boolean) {
        database.transaction {
            checkpointStorage.updateCompatible(runId, compatible)
        }
    }

    @Suppress("LongParameterList")
    fun <A> createFlowFromLogic(
        flowId: StateMachineRunId,
        invocationContext: InvocationContext,
        flowLogic: FlowLogic<A>,
        flowStart: FlowStart,
        ourIdentity: Party,
        existingCheckpoint: Checkpoint?,
        deduplicationHandler: DeduplicationHandler?,
        senderUUID: String?): Flow<A> {
        // Before we construct the state machine state by freezing the FlowLogic we need to make sure that lazy properties
        // have access to the fiber (and thereby the service hub)
        val flowStateMachineImpl = FlowStateMachineImpl(flowId, flowLogic, scheduler)
        val resultFuture = openFuture<Any?>()
        flowStateMachineImpl.transientValues = createTransientValues(flowId, resultFuture)
        flowLogic.stateMachine = flowStateMachineImpl
        val frozenFlowLogic = (flowLogic as FlowLogic<*>).checkpointSerialize(context = checkpointSerializationContext)
        val flowCorDappVersion = FlowStateMachineImpl.createSubFlowVersion(
            serviceHub.cordappProvider.getCordappForFlow(flowLogic), serviceHub.myInfo.platformVersion)

        val checkpoint = existingCheckpoint?.copy(status = Checkpoint.FlowStatus.RUNNABLE) ?: Checkpoint.create(
            invocationContext,
            flowStart,
            flowLogic.javaClass,
            frozenFlowLogic,
            ourIdentity,
            flowCorDappVersion,
            flowLogic.isEnabledTimedFlow()
        ).getOrThrow()

        val state = createStateMachineState(
            checkpoint = checkpoint,
            fiber = flowStateMachineImpl,
            anyCheckpointPersisted = existingCheckpoint != null,
            reloadCheckpointAfterSuspendCount = if (reloadCheckpointAfterSuspend) 0 else null,
            lock = Semaphore(1),
            deduplicationHandler = deduplicationHandler,
            senderUUID = senderUUID
        )
        flowStateMachineImpl.transientState = state
        return  Flow(flowStateMachineImpl, resultFuture)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun Checkpoint.getFiberFromCheckpoint(runId: StateMachineRunId, firstRestore: Boolean): FlowStateMachineImpl<*>? {
        try {
            return when(flowState) {
                is FlowState.Unstarted -> {
                    val logic = deserializeFlowState(flowState.frozenFlowLogic)
                    FlowStateMachineImpl(runId, logic, scheduler)
                }
                is FlowState.Started -> deserializeFlowState(flowState.frozenFiber)
                // Places calling this function is rely on it to return null if the flow cannot be created from the checkpoint.
                else -> return null
            }
        } catch (e: Exception) {
            if (reloadCheckpointAfterSuspend && FlowStateMachineImpl.currentStateMachine() != null) {
                logger.error(
                        "Unable to deserialize checkpoint for flow $runId. [reloadCheckpointAfterSuspend] is turned on, throwing exception",
                        e
                )
                throw ReloadFlowFromCheckpointException(e)
            } else {
                logSerializationError(firstRestore, runId, e)
                return null
            }
        }
    }

    private inline fun <reified T : Any> deserializeFlowState(bytes: SerializedBytes<T>): T {
        return bytes.checkpointDeserialize(context = checkpointSerializationContext)
    }

    private fun logSerializationError(firstRestore: Boolean, flowId: StateMachineRunId, exception: Exception) {
        if (firstRestore) {
            logger.warn("Flow with id $flowId could not be restored from its checkpoint. Normally this means that a CorDapp has been" +
                    " upgraded without draining the node. To run this flow restart the node after downgrading the CorDapp.", exception)
        } else {
            logger.error("Unable to deserialize fiber for flow $flowId. Something is very wrong and this flow will be ignored.", exception)
        }
    }

    private fun verifyFlowLogicIsSuspendable(logic: FlowLogic<Any?>) {
        // Quasar requires (in Java 8) that at least the call method be annotated suspendable. Unfortunately, it's
        // easy to forget to add this when creating a new flow, so we check here to give the user a better error.
        //
        // The Kotlin compiler can sometimes generate a synthetic bridge method from a single call declaration, which
        // forwards to the void method and then returns Unit. However annotations do not get copied across to this
        // bridge, so we have to do a more complex scan here.
        val call = logic.javaClass.methods.first { !it.isSynthetic && it.name == "call" && it.parameterCount == 0 }
        if (call.getAnnotation(Suspendable::class.java) == null) {
            throw FlowException("${logic.javaClass.name}.call() is not annotated as @Suspendable. Please fix this.")
        }
    }

    private fun createTransientValues(id: StateMachineRunId, resultFuture: CordaFuture<Any?>): FlowStateMachineImpl.TransientValues {
        return FlowStateMachineImpl.TransientValues(
            eventQueue = Channels.newChannel(-1, Channels.OverflowPolicy.BLOCK),
            resultFuture = resultFuture,
            database = database,
            transitionExecutor = transitionExecutor,
            actionExecutor = actionExecutor,
            stateMachine = StateMachine(id, secureRandom),
            serviceHub = serviceHub,
            checkpointSerializationContext = checkpointSerializationContext,
            unfinishedFibers = unfinishedFibers,
            waitTimeUpdateHook = { flowId, timeout -> resetCustomTimeout(flowId, timeout) }
        )
    }

    @Suppress("LongParameterList")
    private fun createStateMachineState(
        checkpoint: Checkpoint,
        fiber: FlowStateMachineImpl<*>,
        anyCheckpointPersisted: Boolean,
        reloadCheckpointAfterSuspendCount: Int?,
        lock: Semaphore,
        deduplicationHandler: DeduplicationHandler? = null,
        senderUUID: String? = null
    ): StateMachineState {
        return StateMachineState(
            checkpoint = checkpoint,
            pendingDeduplicationHandlers = deduplicationHandler?.let { listOf(it) } ?: emptyList(),
            isFlowResumed = false,
            future = null,
            isWaitingForFuture = false,
            isAnyCheckpointPersisted = anyCheckpointPersisted,
            isStartIdempotent = false,
            isRemoved = false,
            isKilled = false,
            flowLogic = fiber.logic,
            senderUUID = senderUUID,
            reloadCheckpointAfterSuspendCount = reloadCheckpointAfterSuspendCount,
            lock = lock
        )
    }
}
