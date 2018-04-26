package net.corda.node.services.statemachine

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor

/**
 * A simple [FlowHospital] implementation that immediately triggers error propagation when a flow dirties.
 */
object PropagatingFlowHospital : FlowHospital {
    private val log = loggerFor<PropagatingFlowHospital>()

    override fun flowErrored(flowFiber: FlowFiber) {
        log.debug { "Flow ${flowFiber.id} dirtied ${flowFiber.snapshot().checkpoint.errorState}" }
        flowFiber.scheduleEvent(Event.StartErrorPropagation)
    }

    override fun flowCleaned(flowFiber: FlowFiber) {
        throw IllegalStateException("Flow ${flowFiber.id} cleaned after error propagation triggered")
    }
}
