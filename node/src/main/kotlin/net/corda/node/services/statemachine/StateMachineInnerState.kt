package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.node.services.statemachine.StateMachineManager.Change
import rx.subjects.PublishSubject
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface StateMachineInnerState {
    val lock: Lock
    val flows: MutableMap<StateMachineRunId, Flow<*>>
    val pausedFlows: MutableMap<StateMachineRunId, NonResidentFlow>
    val startedFutures: MutableMap<StateMachineRunId, OpenFuture<Unit>>
    val changesPublisher: PublishSubject<Change>
    /** Flows scheduled to be retried if not finished within the specified timeout period. */
    val timedFlows: MutableMap<StateMachineRunId, ScheduledTimeout>
    val clientIdsToFlowIds: MutableMap<String, FlowWithClientIdStatus>

    fun <R> withMutex(block: StateMachineInnerState.() -> R): R
}

internal class StateMachineInnerStateImpl : StateMachineInnerState {
    /** True if we're shutting down, so don't resume anything. */
    var stopping = false
    override val lock = ReentrantLock()
    override val changesPublisher = PublishSubject.create<Change>()!!
    override val flows = HashMap<StateMachineRunId, Flow<*>>()
    override val pausedFlows = HashMap<StateMachineRunId, NonResidentFlow>()
    override val startedFutures = HashMap<StateMachineRunId, OpenFuture<Unit>>()
    override val timedFlows = HashMap<StateMachineRunId, ScheduledTimeout>()
    override val clientIdsToFlowIds = HashMap<String, FlowWithClientIdStatus>()

    override fun <R> withMutex(block: StateMachineInnerState.() -> R): R = lock.withLock { block(this) }
}

internal inline fun <reified T : StateMachineInnerState, R> T.withLock(block: T.() -> R): R = lock.withLock { block(this) }

internal data class ScheduledTimeout(
    /** Will fire a [FlowTimeoutException] indicating to the flow hospital to restart the flow. */
    val scheduledFuture: ScheduledFuture<*>,
    /** Specifies the number of times this flow has been retried. */
    val retryCount: Int = 0
)