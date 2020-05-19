package net.corda.node.services.statemachine.transitions

import net.corda.node.services.statemachine.*

/**
 * This transition checks the current state of the flow and determines whether anything needs to be done.
 */
class DoRemainingWorkTransition(
        override val context: TransitionContext,
        override val startingState: StateMachineState
) : Transition {
    override fun transition(): TransitionResult {
        val checkpoint = startingState.checkpoint
        // If the flow is removed or has been resumed don't do work.
        if (startingState.isFlowResumed || startingState.isRemoved) {
            return TransitionResult(startingState)
        }
        // Check whether the flow is errored
        return when (checkpoint.errorState) {
            is ErrorState.Clean -> cleanTransition()
            is ErrorState.Errored -> erroredTransition(checkpoint.errorState)
        }
    }

    // If the flow is clean check the FlowState
    private fun cleanTransition(): TransitionResult {
        val flowState = startingState.checkpoint.flowState
        return when (flowState) {
            is FlowState.Unstarted -> UnstartedFlowTransition(context, startingState, flowState).transition()
            is FlowState.Started -> StartedFlowTransition(context, startingState, flowState).transition()
            is FlowState.Completed -> throw IllegalStateException("Cannot transition a state with completed flow state.")
            is FlowState.Paused -> throw IllegalStateException("Cannot transition a state with paused flow state.")
        }
    }

    private fun erroredTransition(errorState: ErrorState.Errored): TransitionResult {
        return ErrorFlowTransition(context, startingState, errorState).transition()
    }
}
