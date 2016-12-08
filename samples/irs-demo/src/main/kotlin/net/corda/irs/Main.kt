package net.corda.irs

import net.corda.core.node.services.ServiceInfo
import net.corda.irs.api.NodeInterestRates
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.type))).get()
        startNode("Bank A")
        startNode("Bank B")
        waitForAllNodesToFinish()
    }, useTestClock = true, isDebug = true)
}
