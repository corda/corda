package net.corda.traderdemo

import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.IssuerFlow
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.BOC
import java.nio.file.Paths

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
        startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type)))
        startNode(DUMMY_BANK_A.name, rpcUsers = demoUser)
        startNode(DUMMY_BANK_B.name, rpcUsers = demoUser)
        startNode(BOC.name, rpcUsers = listOf(user))
        waitForAllNodesToFinish()
    }
}
