package net.corda.notarydemo

import net.corda.node.driver.driver
import net.corda.node.services.transactions.RaftValidatingNotaryService

/** Creates and starts all nodes required for the demo. */
fun main(args: Array<String>) {
    driver(dsl = {
        startNode("Party")
        startNode("Counterparty")
        startNotaryCluster("Raft notary", clusterSize = 3, type = RaftValidatingNotaryService.type)
        waitForAllNodesToFinish()
    }, isDebug = true)
}
