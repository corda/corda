package net.corda.notarydemo

import net.corda.core.div
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.transactions.RaftValidatingNotaryService
import java.nio.file.Paths

/** Creates and starts all nodes required for the demo. */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.notarydemo.flows.DummyIssueAndMove", "StartFlow.net.corda.flows.NotaryFlow\$Client")))
    driver(isDebug = true, driverDirectory = Paths.get("build") / "notary-demo-nodes") {
        startNode("Party", rpcUsers = demoUser)
        startNode("Counterparty")
        startNotaryCluster("Raft notary", clusterSize = 3, type = RaftValidatingNotaryService.type)
        waitForAllNodesToFinish()
    }
}
