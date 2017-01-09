package net.corda.attachmentdemo

import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.transactions.SimpleNotaryService
import java.nio.file.Paths

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
    driver(isDebug = true, driverDirectory = Paths.get("build") / "attachment-demo-nodes") {
        startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.Companion.type)))
        startNode("Bank A", rpcUsers = demoUser)
        startNode("Bank B", rpcUsers = demoUser)
        waitForAllNodesToFinish()
    }
}
