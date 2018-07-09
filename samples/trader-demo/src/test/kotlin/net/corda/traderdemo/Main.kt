package net.corda.traderdemo

import net.corda.core.internal.div
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
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
    driver(DriverParameters(driverDirectory = "build" / "trader-demo-nodes", waitForAllNodesToFinish = true)) {
        val user = User("user1", "test", permissions = setOf(startFlow<CashIssueFlow>(),
                startFlow<CommercialPaperIssueFlow>(),
                startFlow<SellerFlow>()))
        startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = demoUser)
        startNode(providedName = BOC_NAME, rpcUsers = listOf(user))
    }
}
