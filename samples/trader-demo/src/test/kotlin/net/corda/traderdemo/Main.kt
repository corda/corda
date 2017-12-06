package net.corda.traderdemo

import net.corda.core.internal.div
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.internal.config.User
import net.corda.testing.BOC
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.driver.driver
import net.corda.traderdemo.flow.CommercialPaperIssueFlow
import net.corda.traderdemo.flow.SellerFlow

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val permissions = setOf(
            startFlow<CashIssueFlow>(),
            startFlow<SellerFlow>(),
            all())
    val demoUser = listOf(User("demo", "demo", permissions))
    driver(driverDirectory = "build" / "trader-demo-nodes", isDebug = true, waitForAllNodesToFinish = true) {
        val user = User("user1", "test", permissions = setOf(startFlow<CashIssueFlow>(),
                startFlow<CommercialPaperIssueFlow>(),
                startFlow<SellerFlow>()))
        startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser)
        startNode(providedName = BOC.name, rpcUsers = listOf(user))
    }
}
