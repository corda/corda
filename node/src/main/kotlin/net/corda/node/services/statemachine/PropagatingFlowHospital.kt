package net.corda.node.services.statemachine

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor

/**
 * A simple [FlowHospital] implementation that immediately triggers error propagation when a flow dirties.
 */
object PropagatingFlowHospital : FlowHospital {
    private val log = loggerFor<PropagatingFlowHospital>()

    override fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, errors: List<Throwable>) {
        log.debug { "Flow ${flowFiber.id} in state $currentState encountered error" }
        flowFiber.scheduleEvent(Event.StartErrorPropagation)
        for ((index, error) in errors.withIndex()) {
            log.warn("Flow ${flowFiber.id} is propagating error [$index] ", error)
        }
    }

    override fun flowCleaned(flowFiber: FlowFiber) {
        throw IllegalStateException("Flow ${flowFiber.id} cleaned after error propagation triggered")
    }

    override fun flowRemoved(flowFiber: FlowFiber) {}
}
