package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.OpenFuture
import rx.subjects.PublishSubject
import java.util.concurrent.ScheduledFuture

internal class InnerState {
    val changesPublisher = PublishSubject.create<StateMachineManager.Change>()!!
    /** True if we're shutting down, so don't resume anything. */
    var stopping = false
    val flows = HashMap<StateMachineRunId, Flow>()
    val startedFutures = HashMap<StateMachineRunId, OpenFuture<Unit>>()
    /** Flows scheduled to be retried if not finished within the specified timeout period. */
    val timedFlows = HashMap<StateMachineRunId, ScheduledTimeout>()
    val sleepingFlows = HashMap<StateMachineRunId, ScheduledFuture<Unit>>()
}

internal class Flow(val fiber: FlowStateMachineImpl<*>, val resultFuture: OpenFuture<Any?>)

internal data class ScheduledTimeout(
    /** Will fire a [FlowTimeoutException] indicating to the flow hospital to restart the flow. */
    val scheduledFuture: ScheduledFuture<*>,
    /** Specifies the number of times this flow has been retried. */
    val retryCount: Int = 0
)