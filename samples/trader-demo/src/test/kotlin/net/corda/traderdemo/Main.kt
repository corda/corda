package net.corda.traderdemo

import net.corda.core.internal.div
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.security.RPCPermission
import net.corda.testing.setOfPermissionStrings

import net.corda.nodeapi.User
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
    val permissions = setOfPermissionStrings(
            RPCPermission.startFlow<CashIssueFlow>(),
            RPCPermission.startFlow<SellerFlow>(),
            RPCPermission.all)
    val demoUser = listOf(User("demo", "demo", permissions))
    driver(driverDirectory = "build" / "trader-demo-nodes", isDebug = true) {
        val user = User(
            "user1",
            "test",
            setOfPermissionStrings(
                    RPCPermission.startFlow<CashIssueFlow>(),
                    RPCPermission.startFlow<CommercialPaperIssueFlow>(),
                    RPCPermission.startFlow<SellerFlow>()))
        startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser)
        startNode(providedName = BOC.name, rpcUsers = listOf(user))
        waitForAllNodesToFinish()
    }
}
