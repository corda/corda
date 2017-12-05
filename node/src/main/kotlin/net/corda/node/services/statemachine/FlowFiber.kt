package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.transitions.StateMachine

/**
 * An interface wrapping a fiber running a flow.
 */
interface FlowFiber {
    val id: StateMachineRunId
    val stateMachine: StateMachine

    @Suspendable
    fun scheduleEvent(event: Event)

    fun snapshot(): StateMachineState
}
