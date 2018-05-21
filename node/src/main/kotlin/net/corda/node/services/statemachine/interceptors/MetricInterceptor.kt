package net.corda.node.services.statemachine.interceptors

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import net.corda.node.services.statemachine.*
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult

class MetricInterceptor(val metrics: MetricRegistry, val delegate: TransitionExecutor) : TransitionExecutor {
    @Suspendable
    override fun executeTransition(fiber: FlowFiber, previousState: StateMachineState, event: Event, transition: TransitionResult, actionExecutor: ActionExecutor): Pair<FlowContinuation, StateMachineState> {
        val metricActionInterceptor = MetricActionInterceptor(metrics, actionExecutor)
        return delegate.executeTransition(fiber, previousState, event, transition, metricActionInterceptor)
    }
}

class MetricActionInterceptor(val metrics: MetricRegistry, val delegate: ActionExecutor) : ActionExecutor {
    @Suspendable
    override fun executeAction(fiber: FlowFiber, action: Action) {
        val context = metrics.timer("Flows.Actions.${action.javaClass.simpleName}").time()
        delegate.executeAction(fiber, action)
        context.stop()
    }
}