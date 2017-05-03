package net.corda.notarydemo

import com.google.common.net.HostAndPort
import net.corda.core.div
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.flows.NotaryFlow
import net.corda.node.driver.DriverDSL.Companion.notaryNodeName
import net.corda.node.driver.NominatedNetworkMap
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.notarydemo.flows.DummyIssueAndMove
import java.nio.file.Paths

/** Creates and starts all nodes required for the demo. */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf(startFlowPermission<DummyIssueAndMove>(), startFlowPermission<NotaryFlow.Client>())))
    val networkMap = NominatedNetworkMap(notaryNodeName(1), HostAndPort.fromParts("localhost", 10008))
    driver(isDebug = true, driverDirectory = Paths.get("build") / "notary-demo-nodes", networkMapStrategy = networkMap) {
        startNode(ALICE.name, rpcUsers = demoUser)
        startNode(BOB.name)
        startNotaryCluster("Raft", clusterSize = 3, type = RaftValidatingNotaryService.type)
        waitForAllNodesToFinish()
    }
}
