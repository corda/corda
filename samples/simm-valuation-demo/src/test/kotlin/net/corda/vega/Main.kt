package net.corda.vega

import net.corda.core.utilities.getOrThrow
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_BANK_C
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver

/**
 * Sample main used for running within an IDE. Starts 4 nodes (A, B, C and Notary/Controller) as an alternative to running via gradle
 * This does not start any tests but has the nodes running in preparation for a live web demo or to receive commands
 * via the web api.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        val notaryFuture = startNotaryNode(DUMMY_NOTARY.name, validating = false)
        val nodeAFuture = startNode(providedName = DUMMY_BANK_A.name)
        val nodeBFuture = startNode(providedName = DUMMY_BANK_B.name)
        val nodeCFuture = startNode(providedName = DUMMY_BANK_C.name)
        val (nodeA, nodeB, nodeC) = listOf(nodeAFuture, nodeBFuture, nodeCFuture, notaryFuture).map { it.getOrThrow() }

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }, isDebug = true)
}
