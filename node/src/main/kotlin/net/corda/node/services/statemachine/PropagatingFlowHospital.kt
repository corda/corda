package net.corda.node.services.statemachine

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor

/**
 * A simple [FlowHospital] implementation that immediately triggers error propagation when a flow dirties.
 */
object PropagatingFlowHospital : FlowHospital {
    private val log = loggerFor<PropagatingFlowHospital>()

    override fun flowErrored(flowFiber: FlowFiber, currentState: StateMachineState, newError: Throwable): Boolean {
        log.debug { "Flow ${flowFiber.id} $currentState encountered error $newError" }
        flowFiber.scheduleEvent(Event.StartErrorPropagation)
        log.warn("Flow ${flowFiber.id} is propagating", newError)
        return true
    }

    override fun flowCleaned(flowFiber: FlowFiber) {
        throw IllegalStateException("Flow ${flowFiber.id} cleaned after error propagation triggered")
    }

    override fun flowRemoved(flowFiber: FlowFiber) {}
}
