package net.corda.irs

import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
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
        val controllerFuture = startNode(
                providedName = DUMMY_NOTARY.name,
                advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type)))
        val nodeAFuture = startNode(providedName = DUMMY_BANK_A.name)
        val nodeBFuture = startNode(providedName = DUMMY_BANK_B.name)
        val (controller, nodeA, nodeB) = listOf(controllerFuture, nodeAFuture, nodeBFuture).map { it.getOrThrow() }

        waitForAllNodesToFinish()
    }, useTestClock = true, isDebug = true)
}
