package net.corda.node.services.statemachine.interceptors

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.ActionExecutor
import net.corda.node.services.statemachine.ErrorState
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.TransitionExecutor
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult

/**
 * This interceptor notifies the passed in [flowHospital] in case a flow went through a clean->errored or a errored->clean
 * transition.
 */
class HospitalisingInterceptor(
    private val flowHospital: StaffedFlowHospital,
    private val delegate: TransitionExecutor
) : TransitionExecutor {

    @Suspendable
    override fun executeTransition(
        fiber: FlowFiber,
        previousState: StateMachineState,
        event: Event,
        transition: TransitionResult,
        actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {

        // If the fiber's previous state was clean then remove it from the hospital
        // This is important for retrying a flow that has errored during a state machine transition
        if (previousState.checkpoint.errorState is ErrorState.Clean) {
            flowHospital.leave(fiber.id)
        }

        val (continuation, nextState) = delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)

        if (canEnterHospital(previousState, nextState)) {
            val exceptionsToHandle = (nextState.checkpoint.errorState as ErrorState.Errored).errors.map { it.exception }
            flowHospital.requestTreatment(fiber, previousState, exceptionsToHandle)
        }
        if (nextState.isRemoved) {
            removeFlow(fiber.id)
        }
        return Pair(continuation, nextState)
    }

    private fun canEnterHospital(previousState: StateMachineState, nextState: StateMachineState): Boolean {
        return nextState.checkpoint.errorState is ErrorState.Errored
                && (previousState.checkpoint.errorState as? ErrorState.Errored)?.errors != nextState.checkpoint.errorState.errors
    }

    private fun removeFlow(id: StateMachineRunId) {
        flowHospital.leave(id)
        flowHospital.removeMedicalHistory(id)
    }
}
