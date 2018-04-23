/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
        val checkpoint = startingState.checkpoint
        return when (checkpoint.flowState) {
            is FlowState.Unstarted -> UnstartedFlowTransition(context, startingState, checkpoint.flowState).transition()
            is FlowState.Started -> StartedFlowTransition(context, startingState, checkpoint.flowState).transition()
        }
    }

    private fun erroredTransition(errorState: ErrorState.Errored): TransitionResult {
        return ErrorFlowTransition(context, startingState, errorState).transition()
    }
}