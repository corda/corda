package net.corda.vega

import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService

/**
 * Sample main used for running within an IDE. Starts 4 nodes (A, B, C and Notary/Controller) as an alternative to running via gradle
 * This does not start any tests but has the nodes running in preparation for a live web demo or to receive commands
 * via the web api.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.type)))
        startNode("Bank A")
        startNode("Bank B")
        startNode("Bank C")
        waitForAllNodesToFinish()
    }, isDebug = true)
}
