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

class Flow<A>(val fiber: FlowStateMachineImpl<A>, val resultFuture: OpenFuture<Any?>)

class NonResidentFlow(val runId: StateMachineRunId, val checkpoint: Checkpoint) {
    val externalEvents = mutableListOf<Event.DeliverSessionMessage>()

    fun addExternalEvent(message: Event.DeliverSessionMessage) {
        externalEvents.add(message)
    }
}

class FlowCreator(
    val checkpointSerializationContext: CheckpointSerializationContext,
    private val checkpointStorage: CheckpointStorage,
    val scheduler: FiberScheduler,
    val database: CordaPersistence,
    val transitionExecutor: TransitionExecutor,
    val actionExecutor: ActionExecutor,
    val secureRandom: SecureRandom,
    val serviceHub: ServiceHubInternal,
    val unfinishedFibers: ReusableLatch,
    val resetCustomTimeout: (StateMachineRunId, Long) -> Unit) {

    companion object {
        private val logger = contextLogger()
    }

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
        return createFlowFromCheckpoint(nonResidentFlow.runId, checkpoint)
    }

    fun createFlowFromCheckpoint(runId: StateMachineRunId, oldCheckpoint: Checkpoint): Flow<*>? {
        val checkpoint = oldCheckpoint.copy(status = Checkpoint.FlowStatus.RUNNABLE)
        val fiber = checkpoint.getFiberFromCheckpoint(runId) ?: return null
        val resultFuture = openFuture<Any?>()
        fiber.logic.stateMachine = fiber
        verifyFlowLogicIsSuspendable(fiber.logic)
        val state = createStateMachineState(checkpoint, fiber, true)
        fiber.transientValues = createTransientValues(runId, resultFuture)
        fiber.transientState = state
        return Flow(fiber, resultFuture)
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
            checkpoint,
            flowStateMachineImpl,
            existingCheckpoint != null,
             deduplicationHandler,
             senderUUID)
        flowStateMachineImpl.transientState = state
        return  Flow(flowStateMachineImpl, resultFuture)
    }

    private fun Checkpoint.getFiberFromCheckpoint(runId: StateMachineRunId): FlowStateMachineImpl<*>? {
        return when (this.flowState) {
            is FlowState.Unstarted -> {
                val logic = tryCheckpointDeserialize(this.flowState.frozenFlowLogic, runId) ?: return null
                FlowStateMachineImpl(runId, logic, scheduler)
            }
            is FlowState.Started -> tryCheckpointDeserialize(this.flowState.frozenFiber, runId) ?: return null
            // Places calling this function is rely on it to return null if the flow cannot be created from the checkpoint.
            else -> {
                return null
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <reified T : Any> tryCheckpointDeserialize(bytes: SerializedBytes<T>, flowId: StateMachineRunId): T? {
        return try {
            bytes.checkpointDeserialize(context = checkpointSerializationContext)
        } catch (e: Exception) {
            logger.error("Unable to deserialize checkpoint for flow $flowId. Something is very wrong and this flow will be ignored.", e)
            null
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

    private fun createStateMachineState(
            checkpoint: Checkpoint,
            fiber: FlowStateMachineImpl<*>,
            anyCheckpointPersisted: Boolean,
            deduplicationHandler: DeduplicationHandler? = null,
            senderUUID: String? = null): StateMachineState {
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
            senderUUID = senderUUID)
    }
}