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
import co.paralleluniverse.fibers.Fiber.parkAndSerialize
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.channels.Channel
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.node.services.api.FlowAppAuditEvent
import net.corda.node.services.api.FlowPermissionAuditEvent
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.logging.pushToLoggingContext
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.StateMachine
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty1

class FlowPermissionException(message: String) : FlowException(message)

class TransientReference<out A>(@Transient val value: A)

class FlowStateMachineImpl<R>(override val id: StateMachineRunId,
                              override val logic: FlowLogic<R>,
                              scheduler: FiberScheduler,
                              override val creationTime: Long = System.currentTimeMillis()
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R>, FlowFiber {
    companion object {
        /**
         * Return the current [FlowStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): FlowStateMachineImpl<*>? = Strand.currentStrand() as? FlowStateMachineImpl<*>

        // If no CorDapp found then it is a Core flow.
        internal fun createSubFlowVersion(cordapp: Cordapp?, platformVersion: Int): SubFlowVersion {
            return cordapp?.let { SubFlowVersion.CorDappFlow(platformVersion, it.name, it.jarHash) }
                    ?: SubFlowVersion.CoreFlow(platformVersion)
        }

        private val log: Logger = LoggerFactory.getLogger("net.corda.flow")

        private val SERIALIZER_BLOCKER = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER").apply { isAccessible = true }.get(null)
    }

    override val serviceHub get() = getTransientField(TransientValues::serviceHub)

    data class TransientValues(
            val eventQueue: Channel<Event>,
            val resultFuture: CordaFuture<Any?>,
            val database: CordaPersistence,
            val transitionExecutor: TransitionExecutor,
            val actionExecutor: ActionExecutor,
            val stateMachine: StateMachine,
            val serviceHub: ServiceHubInternal,
            val checkpointSerializationContext: SerializationContext,
            val unfinishedFibers: ReusableLatch
    )

    internal var transientValues: TransientReference<TransientValues>? = null
    internal var transientState: TransientReference<StateMachineState>? = null

    /**
     * What sender identifier to put on messages sent by this flow.  This will either be the identifier for the current
     * state machine manager / messaging client, or null to indicate this flow is restored from a checkpoint and
     * the de-duplication of messages it sends should not be optimised since this could be unreliable.
     */
    override val ourSenderUUID: String?
        get() = transientState?.value?.senderUUID

    private fun <A> getTransientField(field: KProperty1<TransientValues, A>): A {
        val suppliedValues = transientValues ?: throw IllegalStateException("${field.name} wasn't supplied!")
        return field.get(suppliedValues.value)
    }

    private fun extractThreadLocalTransaction(): TransientReference<DatabaseTransaction> {
        val transaction = contextTransaction
        contextTransactionOrNull = null
        return TransientReference(transaction)
    }

    /**
     * Return the logger for this state machine. The logger name incorporates [id] and so including it in the log message
     * is not necessary.
     */
    override val logger = log
    override val resultFuture: CordaFuture<R> get() = uncheckedCast(getTransientField(TransientValues::resultFuture))
    override val context: InvocationContext get() = transientState!!.value.checkpoint.invocationContext
    override val ourIdentity: Party get() = transientState!!.value.checkpoint.ourIdentity
    internal var hasSoftLockedStates: Boolean = false
        set(value) {
            if (value) field = value else throw IllegalArgumentException("Can only set to true")
        }

    /**
     * Processes an event by creating the associated transition and executing it using the given executor.
     * Try to avoid using this directly, instead use [processEventsUntilFlowIsResumed] or [processEventImmediately]
     * instead.
     */
    @Suspendable
    private fun processEvent(transitionExecutor: TransitionExecutor, event: Event): FlowContinuation {
        setLoggingContext()
        val stateMachine = getTransientField(TransientValues::stateMachine)
        val oldState = transientState!!.value
        val actionExecutor = getTransientField(TransientValues::actionExecutor)
        val transition = stateMachine.transition(event, oldState)
        val (continuation, newState) = transitionExecutor.executeTransition(this, oldState, event, transition, actionExecutor)
        transientState = TransientReference(newState)
        setLoggingContext()
        return continuation
    }

    /**
     * Processes the events in the event queue until a transition indicates that control should be returned to user code
     * in the form of a regular resume or a throw of an exception. Alternatively the transition may abort the fiber
     * completely.
     *
     * @param isDbTransactionOpenOnEntry indicates whether a DB transaction is expected to be present before the
     *   processing of the eventloop. Purely used for internal invariant checks.
     * @param isDbTransactionOpenOnExit indicates whether a DB transaction is expected to be present once the eventloop
     *   processing finished. Purely used for internal invariant checks.
     */
    @Suspendable
    private fun processEventsUntilFlowIsResumed(isDbTransactionOpenOnEntry: Boolean, isDbTransactionOpenOnExit: Boolean): Any? {
        checkDbTransaction(isDbTransactionOpenOnEntry)
        val transitionExecutor = getTransientField(TransientValues::transitionExecutor)
        val eventQueue = getTransientField(TransientValues::eventQueue)
        try {
            eventLoop@ while (true) {
                val nextEvent = try {
                    eventQueue.receive()
                } catch (interrupted: InterruptedException) {
                    log.error("Flow interrupted while waiting for events, aborting immediately")
                    abortFiber()
                }
                val continuation = processEvent(transitionExecutor, nextEvent)
                when (continuation) {
                    is FlowContinuation.Resume -> return continuation.result
                    is FlowContinuation.Throw -> {
                        continuation.throwable.fillInStackTrace()
                        throw continuation.throwable
                    }
                    FlowContinuation.ProcessEvents -> continue@eventLoop
                    FlowContinuation.Abort -> abortFiber()
                }
            }
        } finally {
            checkDbTransaction(isDbTransactionOpenOnExit)
        }
    }

    /**
     * Immediately processes the passed in event. Always called with an open database transaction.
     *
     * @param event the event to be processed.
     * @param isDbTransactionOpenOnEntry indicates whether a DB transaction is expected to be present before the
     *   processing of the event. Purely used for internal invariant checks.
     * @param isDbTransactionOpenOnExit indicates whether a DB transaction is expected to be present once the event
     *   processing finished. Purely used for internal invariant checks.
     */
    @Suspendable
    private fun processEventImmediately(
            event: Event,
            isDbTransactionOpenOnEntry: Boolean,
            isDbTransactionOpenOnExit: Boolean): FlowContinuation {
        checkDbTransaction(isDbTransactionOpenOnEntry)
        val transitionExecutor = getTransientField(TransientValues::transitionExecutor)
        val continuation = processEvent(transitionExecutor, event)
        checkDbTransaction(isDbTransactionOpenOnExit)
        return continuation
    }

    private fun checkDbTransaction(isPresent: Boolean) {
        if (isPresent) {
            requireNotNull(contextTransactionOrNull)
        } else {
            require(contextTransactionOrNull == null)
        }
    }

    fun setLoggingContext() {
        context.pushToLoggingContext()
        MDC.put("flow-id", id.uuid.toString())
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }

    @Suspendable
    override fun run() {
        logic.stateMachine = this

        setLoggingContext()

        initialiseFlow()

        logger.debug { "Calling flow: $logic" }
        val startTime = System.nanoTime()
        val resultOrError = try {
            val result = logic.call()
            suspend(FlowIORequest.WaitForSessionConfirmations, maySkipCheckpoint = true)
            Try.Success(result)
        } catch (throwable: Throwable) {
            logger.info("Flow threw exception... sending to flow hospital", throwable)
            Try.Failure<R>(throwable)
        }
        val softLocksId = if (hasSoftLockedStates) logic.runId.uuid else null
        val finalEvent = when (resultOrError) {
            is Try.Success -> {
                Event.FlowFinish(resultOrError.value, softLocksId)
            }
            is Try.Failure -> {
                Event.Error(resultOrError.exception)
            }
        }
        // Immediately process the last event. This is to make sure the transition can assume that it has an open
        // database transaction.
        val continuation = processEventImmediately(
                finalEvent,
                isDbTransactionOpenOnEntry = true,
                isDbTransactionOpenOnExit = false
        )
        if (continuation == FlowContinuation.ProcessEvents) {
            // This can happen in case there was an error and there are further things to do e.g. to propagate it.
            processEventsUntilFlowIsResumed(
                    isDbTransactionOpenOnEntry = false,
                    isDbTransactionOpenOnExit = false
            )
        }

        recordDuration(startTime)
        getTransientField(TransientValues::unfinishedFibers).countDown()
    }

    @Suspendable
    private fun initialiseFlow() {
        processEventsUntilFlowIsResumed(
                isDbTransactionOpenOnEntry = false,
                isDbTransactionOpenOnExit = true
        )
    }

    @Suspendable
    override fun <R> subFlow(subFlow: FlowLogic<R>): R {
        checkpointIfSubflowIdempotent(subFlow.javaClass)
        processEventImmediately(
                Event.EnterSubFlow(subFlow.javaClass,
                        createSubFlowVersion(
                               serviceHub.cordappProvider.getCordappForFlow(subFlow), serviceHub.myInfo.platformVersion
                        )
                ),
                isDbTransactionOpenOnEntry = true,
                isDbTransactionOpenOnExit = true
        )
        return try {
            subFlow.call()
        } finally {
            processEventImmediately(
                    Event.LeaveSubFlow,
                    isDbTransactionOpenOnEntry = true,
                    isDbTransactionOpenOnExit = true
            )
        }
    }

    /**
     * If the sub-flow is [IdempotentFlow] we need to perform a checkpoint to make sure any potentially side-effect
     * generating logic between the last checkpoint and the sub-flow invocation does not get replayed if the
     * flow restarts.
     *
     * We don't checkpoint if the current flow is [IdempotentFlow] as well.
     */
    @Suspendable
    private fun checkpointIfSubflowIdempotent(subFlow: Class<FlowLogic<*>>) {
        val currentFlow = snapshot().checkpoint.subFlowStack.last().flowClass
        if (!currentFlow.isIdempotentFlow() && subFlow.isIdempotentFlow()) {
            suspend(FlowIORequest.ForceCheckpoint, false)
        }
    }

    @Suspendable
    override fun initiateFlow(party: Party): FlowSession {
        val resume = processEventImmediately(
                Event.InitiateFlow(party),
                isDbTransactionOpenOnEntry = true,
                isDbTransactionOpenOnExit = true
        ) as FlowContinuation.Resume
        return resume.result as FlowSession
    }

    @Suspendable
    private fun abortFiber(): Nothing {
        while (true) {
            Fiber.park()
        }
    }

    // TODO Dummy implementation of access to application specific permission controls and audit logging
    override fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>) {
        val permissionGranted = true // TODO define permission control service on ServiceHubInternal and actually check authorization.
        val checkPermissionEvent = FlowPermissionAuditEvent(
                serviceHub.clock.instant(),
                context,
                "Flow Permission Required: $permissionName",
                extraAuditData,
                logic.javaClass,
                id,
                permissionName,
                permissionGranted)
        serviceHub.auditService.recordAuditEvent(checkPermissionEvent)
        @Suppress("ConstantConditionIf")
        if (!permissionGranted) {
            throw FlowPermissionException("User ${context.principal()} not permissioned for $permissionName on flow $id")
        }
    }

    // TODO Dummy implementation of access to application specific audit logging
    override fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>) {
        val flowAuditEvent = FlowAppAuditEvent(
                serviceHub.clock.instant(),
                context,
                comment,
                extraAuditData,
                logic.javaClass,
                id,
                eventType)
        serviceHub.auditService.recordAuditEvent(flowAuditEvent)
    }

    @Suspendable
    override fun flowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot? {
        return FlowStackSnapshotFactory.instance.getFlowStackSnapshot(flowClass)
    }

    override fun persistFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>) {
        FlowStackSnapshotFactory.instance.persistAsJsonFile(flowClass, serviceHub.configuration.baseDirectory, id)
    }

    @Suspendable
    override fun <R : Any> suspend(ioRequest: FlowIORequest<R>, maySkipCheckpoint: Boolean): R {
        val serializationContext = TransientReference(getTransientField(TransientValues::checkpointSerializationContext))
        val transaction = extractThreadLocalTransaction()
        parkAndSerialize { _, _ ->
            setLoggingContext()
            logger.trace { "Suspended on $ioRequest" }

            // Will skip checkpoint if there are any idempotent flows in the subflow stack.
            val skipPersistingCheckpoint = containsIdempotentFlows() || maySkipCheckpoint

            contextTransactionOrNull = transaction.value
            val event = try {
                Event.Suspend(
                        ioRequest = ioRequest,
                        maySkipCheckpoint = skipPersistingCheckpoint,
                        fiber = this.serialize(context = serializationContext.value)
                )
            } catch (throwable: Throwable) {
                Event.Error(throwable)
            }

            // We must commit the database transaction before returning from this closure otherwise Quasar may schedule
            // other fibers, so we process the event immediately
            val continuation = processEventImmediately(
                    event,
                    isDbTransactionOpenOnEntry = true,
                    isDbTransactionOpenOnExit = false
            )
            require(continuation == FlowContinuation.ProcessEvents)
            unpark(SERIALIZER_BLOCKER)
        }
        return uncheckedCast(processEventsUntilFlowIsResumed(
                isDbTransactionOpenOnEntry = false,
                isDbTransactionOpenOnExit = true
        ))
    }

    private fun containsIdempotentFlows(): Boolean {
        val subFlowStack = snapshot().checkpoint.subFlowStack
        return subFlowStack.any { IdempotentFlow::class.java.isAssignableFrom(it.flowClass) }
    }

    @Suspendable
    override fun scheduleEvent(event: Event) {
        getTransientField(TransientValues::eventQueue).send(event)
    }

    override fun snapshot(): StateMachineState {
        return transientState!!.value
    }

    override val stateMachine get() = getTransientField(TransientValues::stateMachine)

    /**
     * Records the duration of this flow – from call() to completion or failure.
     * Note that the duration will include the time the flow spent being parked, and not just the total
     * execution time.
     */
    private fun recordDuration(startTime: Long, success: Boolean = true) {
        val timerName = "FlowDuration.${if (success) "Success" else "Failure"}.${logic.javaClass.name}"
        val timer = serviceHub.monitoringService.metrics.timer(timerName)
        // Start time gets serialized along with the fiber when it suspends
        val duration = System.nanoTime() - startTime
        timer.update(duration, TimeUnit.NANOSECONDS)
    }
}

val Class<out FlowLogic<*>>.flowVersionAndInitiatingClass: Pair<Int, Class<out FlowLogic<*>>>
    get() {
        var current: Class<*> = this
        var found: Pair<Int, Class<out FlowLogic<*>>>? = null
        while (true) {
            val annotation = current.getDeclaredAnnotation(InitiatingFlow::class.java)
            if (annotation != null) {
                if (found != null) throw IllegalArgumentException("${InitiatingFlow::class.java.name} can only be annotated once")
                require(annotation.version > 0) { "Flow versions have to be greater or equal to 1" }
                found = annotation.version to uncheckedCast(current)
            }
            current = current.superclass
                    ?: return found
                    ?: throw IllegalArgumentException("$name, as a flow that initiates other flows, must be annotated with " +
                    "${InitiatingFlow::class.java.name}. See https://docs.corda.net/api-flows.html#flowlogic-annotations.")
        }
    }

val Class<out FlowLogic<*>>.appName: String
    get() {
        val jarFile = location.toPath()
        return if (jarFile.isRegularFile() && jarFile.toString().endsWith(".jar")) {
            jarFile.fileName.toString().removeSuffix(".jar")
        } else {
            "<unknown>"
        }
    }
