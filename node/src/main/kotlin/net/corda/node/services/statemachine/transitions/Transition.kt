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

import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.StateMachineState
import java.security.SecureRandom

/**
 * An interface used to separate out different parts of the state machine transition function.
 */
interface Transition {
    /** The context of the transition. */
    val context: TransitionContext
    /** The state the transition is starting in. */
    val startingState: StateMachineState
    /** The (almost) pure transition function. The only side-effect we allow is random number generation. */
    fun transition(): TransitionResult

    /**
     * A helper
     */
    fun builder(build: TransitionBuilder.() -> FlowContinuation): TransitionResult {
        val builder = TransitionBuilder(context, startingState)
        val continuation = build(builder)
        return TransitionResult(builder.currentState, builder.actions, continuation)
    }
}

class TransitionContext(
        val id: StateMachineRunId,
        val configuration: StateMachineConfiguration,
        val secureRandom: SecureRandom
)
