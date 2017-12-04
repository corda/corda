package net.corda.attachmentdemo

import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.User
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
    driver(isDebug = true, driverDirectory = "build" / "attachment-demo-nodes", waitForAllNodesToFinish = true) {
        startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser)
    }
}
