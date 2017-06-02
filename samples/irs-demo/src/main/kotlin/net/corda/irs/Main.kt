package net.corda.irs

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_NOTARY
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
                startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type))),
                startNode(DUMMY_BANK_A.name),
                startNode(DUMMY_BANK_B.name)
        ).getOrThrow()

        startWebserver(controller)
        startWebserver(nodeA)
        startWebserver(nodeB)

        waitForAllNodesToFinish()
    }, useTestClock = true, isDebug = true)
}
