package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.*
import net.corda.node.services.statemachine.*
import java.security.SecureRandom

/**
 * @property eventQueueSize the size of a flow's event queue. If the queue gets full the thread scheduling the event
 *     will block. An example scenario would be if the flow is waiting for a lot of messages at once, but is slow at
 *     processing each.
 */
data class StateMachineConfiguration(
        val eventQueueSize: Int
) {
    companion object {
        val default = StateMachineConfiguration(
                eventQueueSize = 16
        )
    }
}

class StateMachine(
        val id: StateMachineRunId,
        val configuration: StateMachineConfiguration,
        val secureRandom: SecureRandom
) {
    fun transition(event: Event, state: StateMachineState): TransitionResult {
        return TopLevelTransition(TransitionContext(id, configuration, secureRandom), state, event).transition()
    }
}
