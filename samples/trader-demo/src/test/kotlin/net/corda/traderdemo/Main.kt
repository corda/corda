package net.corda.traderdemo

import net.corda.core.internal.div
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.CashIssueFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.BOC
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver
import net.corda.traderdemo.flow.CommercialPaperIssueFlow
import net.corda.traderdemo.flow.SellerFlow

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val permissions = setOf(
            startFlowPermission<CashIssueFlow>(),
            startFlowPermission<SellerFlow>())
    val demoUser = listOf(User("demo", "demo", permissions))
    driver(driverDirectory = "build" / "trader-demo-nodes", isDebug = true) {
        val user = User("user1", "test", permissions = setOf(startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CommercialPaperIssueFlow>(),
                startFlowPermission<SellerFlow>()))
        startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type)))
        startNode(DUMMY_BANK_A.name, rpcUsers = demoUser)
        startNode(DUMMY_BANK_B.name, rpcUsers = demoUser)
        startNode(BOC.name, rpcUsers = listOf(user))
        waitForAllNodesToFinish()
    }
}
