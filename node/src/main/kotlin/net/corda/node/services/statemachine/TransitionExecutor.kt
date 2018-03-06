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
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult

/**
 * An executor of state machine transitions. This is mostly a wrapper interface around an [ActionExecutor], but can be
 * used to create interceptors of transitions.
 */
interface TransitionExecutor {
    @Suspendable
    fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState>
}

/**
 * An interceptor of a transition. These are currently explicitly hooked up in [MultiThreadedStateMachineManager].
 */
typealias TransitionInterceptor = (TransitionExecutor) -> TransitionExecutor
