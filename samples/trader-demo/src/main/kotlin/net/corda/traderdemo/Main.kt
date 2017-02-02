package net.corda.traderdemo

import net.corda.flows.IssuerFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.BOC
import java.nio.file.Paths
import net.corda.core.div

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val permissions = setOf(
            startFlowPermission<IssuerFlow.IssuanceRequester>(),
            startFlowPermission<net.corda.traderdemo.flow.SellerFlow>())
    val demoUser = listOf(User("demo", "demo", permissions))
    driver(driverDirectory = Paths.get("build") / "trader-demo-nodes", isDebug = true) {
        val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))
        startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
        startNode("Bank A", rpcUsers = demoUser)
        startNode("Bank B", rpcUsers = demoUser)
        startNode(BOC.name, rpcUsers = listOf(user))
        waitForAllNodesToFinish()
    }
}
