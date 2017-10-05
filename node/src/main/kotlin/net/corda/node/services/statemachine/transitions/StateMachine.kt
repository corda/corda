package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.*
import net.corda.node.services.statemachine.*
import java.security.SecureRandom

enum class SessionDeliverPersistenceStrategy {
    OnDeliver,
    OnNextCommit
}

data class StateMachineConfiguration(
        val sessionDeliverPersistenceStrategy: SessionDeliverPersistenceStrategy
) {
    companion object {
        val default = StateMachineConfiguration(
                sessionDeliverPersistenceStrategy = SessionDeliverPersistenceStrategy.OnDeliver
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
