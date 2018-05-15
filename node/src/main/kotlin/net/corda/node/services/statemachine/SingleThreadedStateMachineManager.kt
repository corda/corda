/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.fibers.instrument.SuspendableHelper
import co.paralleluniverse.strands.channels.Channels
import com.codahale.metrics.Gauge
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.castIfPossible
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.shouldCheckCheckpoints
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.node.services.statemachine.interceptors.*
import net.corda.node.services.statemachine.transitions.StateMachine
import net.corda.node.services.statemachine.transitions.StateMachineConfiguration
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.serialization.SerializeAsTokenContextImpl
import net.corda.nodeapi.internal.serialization.withTokenContext
import org.apache.activemq.artemis.utils.ReusableLatch
import rx.Observable
import rx.subjects.PublishSubject
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.ArrayList
import kotlin.streams.toList

/**
 * The StateMachineManagerImpl will always invoke the flow fibers on the given [AffinityExecutor], regardless of which
 * thread actually starts them via [startFlow].
 */
@ThreadSafe
class SingleThreadedStateMachineManager(
        val serviceHub: ServiceHubInternal,
        val checkpointStorage: CheckpointStorage,
        val executor: ExecutorService,
        val database: CordaPersistence,
        val secureRandom: SecureRandom,
        private val unfinishedFibers: ReusableLatch = ReusableLatch(),
        private val classloader: ClassLoader = SingleThreadedStateMachineManager::class.java.classLoader
) : StateMachineManager, StateMachineManagerInternal {
    companion object {
        private val logger = contextLogger()
    }

    private class Flow(val fiber: FlowStateMachineImpl<*>, val resultFuture: OpenFuture<Any?>)

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private class InnerState {
        val changesPublisher = PublishSubject.create<StateMachineManager.Change>()!!
        // True if we're shutting down, so don't resume anything.
        var stopping = false
        val flows = HashMap<StateMachineRunId, Flow>()
        val startedFutures = HashMap<StateMachineRunId, OpenFuture<Unit>>()
    }

    private val mutex = ThreadBox(InnerState())
    private val scheduler = FiberExecutorScheduler("Same thread scheduler", executor)
    // How many Fibers are running and not suspended.  If zero and stopping is true, then we are halted.
    private val liveFibers = ReusableLatch()
    // Monitoring support.
    private val metrics = serviceHub.monitoringService.metrics
    private val sessionToFlow = ConcurrentHashMap<SessionId, StateMachineRunId>()
    private val flowMessaging: FlowMessaging = FlowMessagingImpl(serviceHub)
    private val fiberDeserializationChecker = if (serviceHub.configuration.shouldCheckCheckpoints()) FiberDeserializationChecker() else null
    private val transitionExecutor = makeTransitionExecutor()

    private var checkpointSerializationContext: SerializationContext? = null
    private var tokenizableServices: List<Any>? = null
    private var actionExecutor: ActionExecutor? = null

    override val allStateMachines: List<FlowLogic<*>>
        get() = mutex.locked { flows.values.map { it.fiber.logic } }


    private val totalStartedFlows = metrics.counter("Flows.Started")
    private val totalFinishedFlows = metrics.counter("Flows.Finished")

    /**
     * An observable that emits triples of the changing flow, the type of change, and a process-specific ID number
     * which may change across restarts.
     *
     * We use assignment here so that multiple subscribers share the same wrapped Observable.
     */
    override val changes: Observable<StateMachineManager.Change> = mutex.content.changesPublisher

    override fun start(tokenizableServices: List<Any>) {
        checkQuasarJavaAgentPresence()
        this.tokenizableServices = tokenizableServices
        val checkpointSerializationContext = SerializationDefaults.CHECKPOINT_CONTEXT.withTokenContext(
                SerializeAsTokenContextImpl(tokenizableServices, SerializationDefaults.SERIALIZATION_FACTORY, SerializationDefaults.CHECKPOINT_CONTEXT, serviceHub)
        )
        this.checkpointSerializationContext = checkpointSerializationContext
        this.actionExecutor = makeActionExecutor(checkpointSerializationContext)
        fiberDeserializationChecker?.start(checkpointSerializationContext)
        val fibers = restoreFlowsFromCheckpoints()
        metrics.register("Flows.InFlight", Gauge<Int> { mutex.content.flows.size })
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
            (fiber as FlowStateMachineImpl<*>).logger.warn("Caught exception from flow", throwable)
        }
        serviceHub.networkMapCache.nodeReady.then {
            resumeRestoredFlows(fibers)
            flowMessaging.start { receivedMessage, deduplicationHandler ->
                executor.execute {
                    onSessionMessage(receivedMessage, deduplicationHandler)
                }
            }
        }
    }

    override fun <A : FlowLogic<*>> findStateMachines(flowClass: Class<A>): List<Pair<A, CordaFuture<*>>> {
        return mutex.locked {
            flows.values.mapNotNull {
                flowClass.castIfPossible(it.fiber.logic)?.let { it to it.stateMachine.resultFuture }
            }
        }
    }

    /**
     * Start the shutdown process, bringing the [SingleThreadedStateMachineManager] to a controlled stop.  When this method returns,
     * all Fibers have been suspended and checkpointed, or have completed.
     *
     * @param allowedUnsuspendedFiberCount Optional parameter is used in some tests.
     */
    override fun stop(allowedUnsuspendedFiberCount: Int) {
        require(allowedUnsuspendedFiberCount >= 0)
        mutex.locked {
            if (stopping) throw IllegalStateException("Already stopping!")
            stopping = true
            for ((_, flow) in flows) {
                flow.fiber.scheduleEvent(Event.SoftShutdown)
            }
        }
        // Account for any expected Fibers in a test scenario.
        liveFibers.countDown(allowedUnsuspendedFiberCount)
        liveFibers.await()
        fiberDeserializationChecker?.let {
            val foundUnrestorableFibers = it.stop()
            check(!foundUnrestorableFibers) { "Unrestorable checkpoints were created, please check the logs for details." }
        }
    }

    /**
     * Atomic get snapshot + subscribe. This is needed so we don't miss updates between subscriptions to [changes] and
     * calls to [allStateMachines]
     */
    override fun track(): DataFeed<List<FlowLogic<*>>, StateMachineManager.Change> {
        return mutex.locked {
            DataFeed(flows.values.map { it.fiber.logic }, changesPublisher.bufferUntilSubscribed())
        }
    }

    override fun <A> startFlow(
            flowLogic: FlowLogic<A>,
            context: InvocationContext,
            ourIdentity: Party?,
            deduplicationHandler: DeduplicationHandler?
    ): CordaFuture<FlowStateMachine<A>> {
        return startFlowInternal(
                invocationContext = context,
                flowLogic = flowLogic,
                flowStart = FlowStart.Explicit,
                ourIdentity = ourIdentity ?: getOurFirstIdentity(),
                deduplicationHandler = deduplicationHandler,
                isStartIdempotent = false
        )
    }

    override fun killFlow(id: StateMachineRunId): Boolean {

        return mutex.locked {
            val flow = flows.remove(id)
            if (flow != null) {
                logger.debug("Killing flow known to physical node.")
                decrementLiveFibers()
                totalFinishedFlows.inc()
                unfinishedFibers.countDown()
                try {
                    flow.fiber.interrupt()
                    true
                } finally {
                    database.transaction {
                        checkpointStorage.removeCheckpoint(id)
                    }
                }
            } else {
                // TODO replace with a clustered delete after we'll support clustered nodes
                logger.debug("Unable to kill a flow unknown to physical node. Might be processed by another physical node.")
                false
            }
        }
    }

    override fun addSessionBinding(flowId: StateMachineRunId, sessionId: SessionId) {
        val previousFlowId = sessionToFlow.put(sessionId, flowId)
        if (previousFlowId != null) {
            if (previousFlowId == flowId) {
                logger.warn("Session binding from $sessionId to $flowId re-added")
            } else {
                throw IllegalStateException(
                        "Attempted to add session binding from session $sessionId to flow $flowId, " +
                                "however there was already a binding to $previousFlowId"
                )
            }
        }
    }

    override fun removeSessionBindings(sessionIds: Set<SessionId>) {
        val reRemovedSessionIds = HashSet<SessionId>()
        for (sessionId in sessionIds) {
            val flowId = sessionToFlow.remove(sessionId)
            if (flowId == null) {
                reRemovedSessionIds.add(sessionId)
            }
        }
        if (reRemovedSessionIds.isNotEmpty()) {
            logger.warn("Session binding from $reRemovedSessionIds re-removed")
        }
    }

    override fun removeFlow(flowId: StateMachineRunId, removalReason: FlowRemovalReason, lastState: StateMachineState) {
        mutex.locked {
            val flow = flows.remove(flowId)
            if (flow != null) {
                decrementLiveFibers()
                totalFinishedFlows.inc()
                unfinishedFibers.countDown()
                return when (removalReason) {
                    is FlowRemovalReason.OrderlyFinish -> removeFlowOrderly(flow, removalReason, lastState)
                    is FlowRemovalReason.ErrorFinish -> removeFlowError(flow, removalReason, lastState)
                    FlowRemovalReason.SoftShutdown -> flow.fiber.scheduleEvent(Event.SoftShutdown)
                }
            } else {
                logger.warn("Flow $flowId re-finished")
            }
        }
    }

    override fun signalFlowHasStarted(flowId: StateMachineRunId) {
        mutex.locked {
            startedFutures.remove(flowId)?.set(Unit)
            flows[flowId]?.let { flow ->
                changesPublisher.onNext(StateMachineManager.Change.Add(flow.fiber.logic))
            }
        }
    }

    private val stateMachineConfiguration = serviceHub.configuration.enterpriseConfiguration.tuning.stateMachine

    private fun checkQuasarJavaAgentPresence() {
        check(SuspendableHelper.isJavaAgentActive(), {
            """Missing the '-javaagent' JVM argument. Make sure you run the tests with the Quasar java agent attached to your JVM.
               #See https://docs.corda.net/troubleshooting.html - 'Fiber classes not instrumented' for more details.""".trimMargin("#")
        })
    }

    private fun decrementLiveFibers() {
        liveFibers.countDown()
    }

    private fun incrementLiveFibers() {
        liveFibers.countUp()
    }

    private fun restoreFlowsFromCheckpoints(): List<Flow> {
        return checkpointStorage.getAllCheckpoints().map { (id, serializedCheckpoint) ->
            // If a flow is added before start() then don't attempt to restore it
            mutex.locked { if (flows.containsKey(id)) return@map null }
            val checkpoint = deserializeCheckpoint(serializedCheckpoint)
            if (checkpoint == null) return@map null
            createFlowFromCheckpoint(
                    id = id,
                    checkpoint = checkpoint,
                    initialDeduplicationHandler = null,
                    isAnyCheckpointPersisted = true,
                    isStartIdempotent = false
            )
        }.toList().filterNotNull()
    }

    private fun resumeRestoredFlows(flows: List<Flow>) {
        for (flow in flows) {
            addAndStartFlow(flow.fiber.id, flow)
        }
    }

    private fun onSessionMessage(message: ReceivedMessage, deduplicationHandler: DeduplicationHandler) {
        val peer = message.peer
        val sessionMessage = try {
            message.data.deserialize<SessionMessage>()
        } catch (ex: Exception) {
            logger.error("Received corrupt SessionMessage data from $peer")
            deduplicationHandler.afterDatabaseTransaction()
            return
        }
        val sender = serviceHub.networkMapCache.getPeerByLegalName(peer)
        if (sender != null) {
            when (sessionMessage) {
                is ExistingSessionMessage -> onExistingSessionMessage(sessionMessage, deduplicationHandler, sender)
                is InitialSessionMessage -> onSessionInit(sessionMessage, message.platformVersion, deduplicationHandler, sender)
            }
        } else {
            logger.error("Unknown peer $peer in $sessionMessage")
        }
    }

    private fun onExistingSessionMessage(sessionMessage: ExistingSessionMessage, deduplicationHandler: DeduplicationHandler, sender: Party) {
        try {
            val recipientId = sessionMessage.recipientSessionId
            val flowId = sessionToFlow[recipientId]
            if (flowId == null) {
                deduplicationHandler.afterDatabaseTransaction()
                if (sessionMessage.payload === EndSessionMessage) {
                    logger.debug {
                        "Got ${EndSessionMessage::class.java.simpleName} for " +
                                "unknown session $recipientId, discarding..."
                    }
                } else {
                    throw IllegalArgumentException("Cannot find flow corresponding to session ID $recipientId")
                }
            } else {
                val flow = mutex.locked { flows[flowId] } ?: throw IllegalStateException("Cannot find fiber corresponding to ID $flowId")
                flow.fiber.scheduleEvent(Event.DeliverSessionMessage(sessionMessage, deduplicationHandler, sender))
            }
        } catch (exception: Exception) {
            logger.error("Exception while routing $sessionMessage", exception)
            throw exception
        }
    }

    private fun onSessionInit(sessionMessage: InitialSessionMessage, senderPlatformVersion: Int, deduplicationHandler: DeduplicationHandler, sender: Party) {
        fun createErrorMessage(initiatorSessionId: SessionId, message: String): ExistingSessionMessage {
            val errorId = secureRandom.nextLong()
            val payload = RejectSessionMessage(message, errorId)
            return ExistingSessionMessage(initiatorSessionId, payload)
        }
        val replyError = try {
            val initiatedFlowFactory = getInitiatedFlowFactory(sessionMessage)
            val initiatedSessionId = SessionId.createRandom(secureRandom)
            val senderSession = FlowSessionImpl(sender, initiatedSessionId)
            val flowLogic = initiatedFlowFactory.createFlow(senderSession)
            val initiatedFlowInfo = when (initiatedFlowFactory) {
                is InitiatedFlowFactory.Core -> FlowInfo(serviceHub.myInfo.platformVersion, "corda")
                is InitiatedFlowFactory.CorDapp -> FlowInfo(initiatedFlowFactory.flowVersion, initiatedFlowFactory.appName)
            }
            val senderCoreFlowVersion = when (initiatedFlowFactory) {
                is InitiatedFlowFactory.Core -> senderPlatformVersion
                is InitiatedFlowFactory.CorDapp -> null
            }
            startInitiatedFlow(flowLogic, deduplicationHandler, senderSession, initiatedSessionId, sessionMessage, senderCoreFlowVersion, initiatedFlowInfo)
            null
        } catch (exception: Exception) {
            logger.warn("Exception while creating initiated flow", exception)
            createErrorMessage(
                    sessionMessage.initiatorSessionId,
                    (exception as? SessionRejectException)?.message ?: "Unable to establish session"
            )
        }

        if (replyError != null) {
            flowMessaging.sendSessionMessage(sender, replyError, DeduplicationId.createRandom(secureRandom))
            deduplicationHandler.afterDatabaseTransaction()
        }
    }

    // TODO this is a temporary hack until we figure out multiple identities
    private fun getOurFirstIdentity(): Party {
        return serviceHub.myInfo.legalIdentities[0]
    }

    private fun getInitiatedFlowFactory(message: InitialSessionMessage): InitiatedFlowFactory<*> {
        val initiatingFlowClass = try {
            Class.forName(message.initiatorFlowClassName, true, classloader).asSubclass(FlowLogic::class.java)
        } catch (e: ClassNotFoundException) {
            throw SessionRejectException("Don't know ${message.initiatorFlowClassName}")
        } catch (e: ClassCastException) {
            throw SessionRejectException("${message.initiatorFlowClassName} is not a flow")
        }
        return serviceHub.getFlowFactory(initiatingFlowClass) ?:
                throw SessionRejectException("$initiatingFlowClass is not registered")
    }

    private fun <A> startInitiatedFlow(
            flowLogic: FlowLogic<A>,
            initiatingMessageDeduplicationHandler: DeduplicationHandler,
            peerSession: FlowSessionImpl,
            initiatedSessionId: SessionId,
            initiatingMessage: InitialSessionMessage,
            senderCoreFlowVersion: Int?,
            initiatedFlowInfo: FlowInfo
    ) {
        val flowStart = FlowStart.Initiated(peerSession, initiatedSessionId, initiatingMessage, senderCoreFlowVersion, initiatedFlowInfo)
        val ourIdentity = getOurFirstIdentity()
        startFlowInternal(
                InvocationContext.peer(peerSession.counterparty.name), flowLogic, flowStart, ourIdentity,
                initiatingMessageDeduplicationHandler,
                isStartIdempotent = false
        )
    }

    private fun <A> startFlowInternal(
            invocationContext: InvocationContext,
            flowLogic: FlowLogic<A>,
            flowStart: FlowStart,
            ourIdentity: Party,
            deduplicationHandler: DeduplicationHandler?,
            isStartIdempotent: Boolean
    ): CordaFuture<FlowStateMachine<A>> {
        val flowId = StateMachineRunId.createRandom()
        val deduplicationSeed = when (flowStart) {
            FlowStart.Explicit -> flowId.uuid.toString()
            is FlowStart.Initiated ->
                "${flowStart.initiatingMessage.initiatorSessionId.toLong}-" +
                        "${flowStart.initiatingMessage.initiationEntropy}"
        }

        // Before we construct the state machine state by freezing the FlowLogic we need to make sure that lazy properties
        // have access to the fiber (and thereby the service hub)
        val flowStateMachineImpl = FlowStateMachineImpl(flowId, flowLogic, scheduler)
        val resultFuture = openFuture<Any?>()
        flowStateMachineImpl.transientValues = TransientReference(createTransientValues(flowId, resultFuture))
        flowLogic.stateMachine = flowStateMachineImpl
        val frozenFlowLogic = (flowLogic as FlowLogic<*>).serialize(context = checkpointSerializationContext!!)

        val initialCheckpoint = Checkpoint.create(invocationContext, flowStart, flowLogic.javaClass, frozenFlowLogic, ourIdentity, deduplicationSeed).getOrThrow()
        val startedFuture = openFuture<Unit>()
        val initialState = StateMachineState(
                checkpoint = initialCheckpoint,
                pendingDeduplicationHandlers = deduplicationHandler?.let { listOf(it) } ?: emptyList(),
                isFlowResumed = false,
                isTransactionTracked = false,
                isAnyCheckpointPersisted = false,
                isStartIdempotent = isStartIdempotent,
                isRemoved = false,
                flowLogic = flowLogic
        )
        flowStateMachineImpl.transientState = TransientReference(initialState)
        mutex.locked {
            startedFutures[flowId] = startedFuture
        }
        totalStartedFlows.inc()
        addAndStartFlow(flowId, Flow(flowStateMachineImpl, resultFuture))
        return startedFuture.map { flowStateMachineImpl as FlowStateMachine<A> }
    }

    private fun deserializeCheckpoint(serializedCheckpoint: SerializedBytes<Checkpoint>): Checkpoint? {
        return try {
            serializedCheckpoint.deserialize(context = checkpointSerializationContext!!)
        } catch (exception: Throwable) {
            logger.error("Encountered unrestorable checkpoint!", exception)
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
                eventQueue = Channels.newChannel(stateMachineConfiguration.eventQueueSize, Channels.OverflowPolicy.BLOCK),
                resultFuture = resultFuture,
                database = database,
                transitionExecutor = transitionExecutor,
                actionExecutor = actionExecutor!!,
                stateMachine = StateMachine(id, stateMachineConfiguration, secureRandom),
                serviceHub = serviceHub,
                checkpointSerializationContext = checkpointSerializationContext!!
        )
    }

    private fun createFlowFromCheckpoint(
            id: StateMachineRunId,
            checkpoint: Checkpoint,
            isAnyCheckpointPersisted: Boolean,
            isStartIdempotent: Boolean,
            initialDeduplicationHandler: DeduplicationHandler?
    ): Flow {
        val flowState = checkpoint.flowState
        val resultFuture = openFuture<Any?>()
        val fiber = when (flowState) {
            is FlowState.Unstarted -> {
                val logic = flowState.frozenFlowLogic.deserialize(context = checkpointSerializationContext!!)
                val state = StateMachineState(
                        checkpoint = checkpoint,
                        pendingDeduplicationHandlers = initialDeduplicationHandler?.let { listOf(it) } ?: emptyList(),
                        isFlowResumed = false,
                        isTransactionTracked = false,
                        isAnyCheckpointPersisted = isAnyCheckpointPersisted,
                        isStartIdempotent = isStartIdempotent,
                        isRemoved = false,
                        flowLogic = logic
                )
                val fiber = FlowStateMachineImpl(id, logic, scheduler)
                fiber.transientValues = TransientReference(createTransientValues(id, resultFuture))
                fiber.transientState = TransientReference(state)
                fiber.logic.stateMachine = fiber
                fiber
            }
            is FlowState.Started -> {
                val fiber = flowState.frozenFiber.deserialize(context = checkpointSerializationContext!!)
                val state = StateMachineState(
                        checkpoint = checkpoint,
                        pendingDeduplicationHandlers = initialDeduplicationHandler?.let { listOf(it) } ?: emptyList(),
                        isFlowResumed = false,
                        isTransactionTracked = false,
                        isAnyCheckpointPersisted = isAnyCheckpointPersisted,
                        isStartIdempotent = isStartIdempotent,
                        isRemoved = false,
                        flowLogic = fiber.logic
                )
                fiber.transientValues = TransientReference(createTransientValues(id, resultFuture))
                fiber.transientState = TransientReference(state)
                fiber.logic.stateMachine = fiber
                fiber
            }
        }

        verifyFlowLogicIsSuspendable(fiber.logic)

        return Flow(fiber, resultFuture)
    }

    private fun addAndStartFlow(id: StateMachineRunId, flow: Flow) {
        val checkpoint = flow.fiber.snapshot().checkpoint
        for (sessionId in getFlowSessionIds(checkpoint)) {
            sessionToFlow[sessionId] = id
        }
        mutex.locked {
            if (stopping) {
                startedFutures[id]?.setException(IllegalStateException("Will not start flow as SMM is stopping"))
                logger.trace("Not resuming as SMM is stopping.")
            } else {
                incrementLiveFibers()
                unfinishedFibers.countUp()
                flows[id] = flow
                flow.fiber.scheduleEvent(Event.DoRemainingWork)
                when (checkpoint.flowState) {
                    is FlowState.Unstarted -> {
                        flow.fiber.start()
                    }
                    is FlowState.Started -> {
                        Fiber.unparkDeserialized(flow.fiber, scheduler)
                    }
                }
            }
        }
    }

    private fun getFlowSessionIds(checkpoint: Checkpoint): Set<SessionId> {
        val initiatedFlowStart = (checkpoint.flowState as? FlowState.Unstarted)?.flowStart as? FlowStart.Initiated
        return if (initiatedFlowStart == null) {
            checkpoint.sessions.keys
        } else {
            checkpoint.sessions.keys + initiatedFlowStart.initiatedSessionId
        }
    }

    private fun makeActionExecutor(checkpointSerializationContext: SerializationContext): ActionExecutor {
        return ActionExecutorImpl(
                serviceHub,
                checkpointStorage,
                flowMessaging,
                this,
                checkpointSerializationContext,
                metrics
        )
    }

    private fun makeTransitionExecutor(): TransitionExecutor {
        val interceptors = ArrayList<TransitionInterceptor>()
        interceptors.add { HospitalisingInterceptor(PropagatingFlowHospital, it) }
        if (serviceHub.configuration.devMode) {
            interceptors.add { DumpHistoryOnErrorInterceptor(it) }
        }
        if (serviceHub.configuration.shouldCheckCheckpoints()) {
            interceptors.add { FiberDeserializationCheckingInterceptor(fiberDeserializationChecker!!, it) }
        }
        if (logger.isDebugEnabled) {
            interceptors.add { PrintingInterceptor(it) }
        }
        val transitionExecutor: TransitionExecutor = TransitionExecutorImpl(secureRandom, database)
        return interceptors.fold(transitionExecutor) { executor, interceptor -> interceptor(executor) }
    }

    private fun InnerState.removeFlowOrderly(
            flow: Flow,
            removalReason: FlowRemovalReason.OrderlyFinish,
            lastState: StateMachineState
    ) {
        drainFlowEventQueue(flow)
        // final sanity checks
        require(lastState.pendingDeduplicationHandlers.isEmpty())
        require(lastState.isRemoved)
        require(lastState.checkpoint.subFlowStack.size == 1)
        sessionToFlow.none { it.value == flow.fiber.id }
        flow.resultFuture.set(removalReason.flowReturnValue)
        lastState.flowLogic.progressTracker?.currentStep = ProgressTracker.DONE
        changesPublisher.onNext(StateMachineManager.Change.Removed(lastState.flowLogic, Try.Success(removalReason.flowReturnValue)))
    }

    private fun InnerState.removeFlowError(
            flow: Flow,
            removalReason: FlowRemovalReason.ErrorFinish,
            lastState: StateMachineState
    ) {
        drainFlowEventQueue(flow)
        val flowError = removalReason.flowErrors[0] // TODO what to do with several?
        val exception = flowError.exception
        (exception as? FlowException)?.originalErrorId = flowError.errorId
        flow.resultFuture.setException(exception)
        lastState.flowLogic.progressTracker?.endWithError(exception)
        changesPublisher.onNext(StateMachineManager.Change.Removed(lastState.flowLogic, Try.Failure<Nothing>(exception)))
    }

    // The flow's event queue may be non-empty in case it shut down abruptly. We handle outstanding events here.
    private fun drainFlowEventQueue(flow: Flow) {
        while (true) {
            val event = flow.fiber.transientValues!!.value.eventQueue.tryReceive() ?: return
            when (event) {
                is Event.DoRemainingWork -> {}
                is Event.DeliverSessionMessage -> {
                    // Acknowledge the message so it doesn't leak in the broker.
                    event.deduplicationHandler.afterDatabaseTransaction()
                    when (event.sessionMessage.payload) {
                        EndSessionMessage -> {
                            logger.debug { "Unhandled message ${event.sessionMessage} due to flow shutting down" }
                        }
                        else -> {
                            logger.warn("Unhandled message ${event.sessionMessage} due to flow shutting down")
                        }
                    }
                }
                else -> {
                    logger.warn("Unhandled event $event due to flow shutting down")
                }
            }
        }
    }
}
