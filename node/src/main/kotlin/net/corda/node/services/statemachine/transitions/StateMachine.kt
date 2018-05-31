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
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.StateMachineState
import java.security.SecureRandom

class StateMachine(
        val id: StateMachineRunId,
        val secureRandom: SecureRandom
) {
    fun transition(event: Event, state: StateMachineState): TransitionResult {
        return TopLevelTransition(TransitionContext(id, secureRandom), state, event).transition()
    }
}
