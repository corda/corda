package net.corda.attachmentdemo

import net.corda.core.internal.div
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
    driver(DriverParameters(driverDirectory = "build" / "attachment-demo-nodes", waitForAllNodesToFinish = true)) {
        startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = demoUser)
    }
}
