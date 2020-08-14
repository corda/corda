package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.StateMachineState
import java.security.SecureRandom
import java.time.Instant

class StateMachine(
        val id: StateMachineRunId,
        val secureRandom: SecureRandom
) {
    fun transition(event: Event, state: StateMachineState, time: Instant): TransitionResult {
        return TopLevelTransition(TransitionContext(id, secureRandom, time), state, event).transition()
    }
}
