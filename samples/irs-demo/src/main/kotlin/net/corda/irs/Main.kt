package net.corda.irs

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
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
        val (controller, nodeA, nodeB) = Futures.allAsList(
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.type))),
                startNode("Bank A"),
                startNode("Bank B")
        ).getOrThrow()

        startWebserver(controller)
        startWebserver(nodeA)
        startWebserver(nodeB)

        waitForAllNodesToFinish()
    }, useTestClock = true, isDebug = true)
}
