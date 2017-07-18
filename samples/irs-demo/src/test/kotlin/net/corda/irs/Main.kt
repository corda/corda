package net.corda.irs

import net.corda.core.internal.concurrent.getOrThrow
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.services.ServiceInfo
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.irs.api.NodeInterestRates
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        val (controller, nodeA, nodeB) = listOf(
                startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type))),
                startNode(DUMMY_BANK_A.name),
                startNode(DUMMY_BANK_B.name)
        ).transpose().getOrThrow()

        startWebserver(controller)
        startWebserver(nodeA)
        startWebserver(nodeB)

        waitForAllNodesToFinish()
    }, useTestClock = true, isDebug = true)
}
