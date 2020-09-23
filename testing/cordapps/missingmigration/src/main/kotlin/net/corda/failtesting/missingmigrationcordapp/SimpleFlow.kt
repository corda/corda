package net.corda.failtesting.missingmigrationcordapp

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

@StartableByRPC
@InitiatingFlow
class SimpleFlow : FlowLogic<Unit>() {
    override fun call() {
        logger.info("Running simple flow doing nothing")
    }
}