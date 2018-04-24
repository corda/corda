/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
