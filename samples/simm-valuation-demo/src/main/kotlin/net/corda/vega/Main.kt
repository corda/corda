package net.corda.vega

import com.google.common.util.concurrent.Futures
import net.corda.core.crypto.X509Utilities
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_BANK_C
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService

/**
 * Sample main used for running within an IDE. Starts 4 nodes (A, B, C and Notary/Controller) as an alternative to running via gradle
 * This does not start any tests but has the nodes running in preparation for a live web demo or to receive commands
 * via the web api.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        startNode(X509Utilities.getDevX509Name("Controller"), setOf(ServiceInfo(SimpleNotaryService.type)))
        val (nodeA, nodeB, nodeC) = Futures.allAsList(
                startNode(DUMMY_BANK_A.name),
                startNode(DUMMY_BANK_B.name),
                startNode(DUMMY_BANK_C.name)
        ).getOrThrow()

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }, isDebug = true)
}
