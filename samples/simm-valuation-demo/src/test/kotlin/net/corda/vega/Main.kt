package net.corda.vega

import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_BANK_C
import net.corda.testing.DUMMY_NOTARY
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.driver.driver

/**
 * Sample main used for running within an IDE. Starts 4 nodes (A, B, C and Notary/Controller) as an alternative to running via gradle
 * This does not start any tests but has the nodes running in preparation for a live web demo or to receive commands
 * via the web api.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        startNode(providedName = DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        val nodeA = startNode(providedName = DUMMY_BANK_A.name).getOrThrow()
        val nodeB = startNode(providedName = DUMMY_BANK_B.name).getOrThrow()
        val nodeC = startNode(providedName = DUMMY_BANK_C.name).getOrThrow()

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }, isDebug = true)
}
