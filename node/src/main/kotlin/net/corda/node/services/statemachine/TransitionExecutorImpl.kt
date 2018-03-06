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

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.utilities.contextLogger
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import java.security.SecureRandom

/**
 * This [TransitionExecutor] runs the transition actions using the passed in [ActionExecutor] and manually dirties the
 * state on failure.
 *
 * If a failure happens when we're already transitioning into a errored state then the transition and the flow fiber is
 * completely aborted to avoid error loops.
 */
class TransitionExecutorImpl(
        val secureRandom: SecureRandom,
        val database: CordaPersistence
) : TransitionExecutor {
    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {
        contextDatabase = database
        for (action in transition.actions) {
            try {
                actionExecutor.executeAction(fiber, action)
            } catch (exception: Throwable) {
                contextTransactionOrNull?.close()
                if (transition.newState.checkpoint.errorState is ErrorState.Errored) {
                    // If we errored while transitioning to an error state then we cannot record the additional
                    // error as that may result in an infinite loop, e.g. error propagation fails -> record error -> propagate fails again.
                    // Instead we just keep around the old error state and wait for a new schedule, perhaps
                    // triggered from a flow hospital
                    log.error("Error while executing $action during transition to errored state, aborting transition", exception)
                    return Pair(FlowContinuation.Abort, previousState.copy(isFlowResumed = false))
                } else {
                    // Otherwise error the state manually keeping the old flow state and schedule a DoRemainingWork
                    // to trigger error propagation
                    log.error("Error while executing $action, erroring state", exception)
                    val newState = previousState.copy(
                            checkpoint = previousState.checkpoint.copy(
                                    errorState = previousState.checkpoint.errorState.addErrors(
                                            listOf(FlowError(secureRandom.nextLong(), exception))
                                    )
                            ),
                            isFlowResumed = false
                    )
                    fiber.scheduleEvent(Event.DoRemainingWork)
                    return Pair(FlowContinuation.ProcessEvents, newState)
                }
            }
        }
        return Pair(transition.continuation, transition.newState)
    }
}
