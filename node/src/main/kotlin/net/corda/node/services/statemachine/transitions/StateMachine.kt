package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.SessionId
import net.corda.node.services.statemachine.StateMachineSerialization
import net.corda.node.services.statemachine.StateMachineState
import java.security.SecureRandom

class StateMachine(
        val id: StateMachineRunId,
        val secureRandom: SecureRandom,
        private val serialization: StateMachineSerialization,
        private val sessionIdFactory: () -> SessionId) {
    fun transition(event: Event, state: StateMachineState): TransitionResult {
        return TopLevelTransition(TransitionContext(id, secureRandom), state, event, serialization, sessionIdFactory).transition()
    }
}
