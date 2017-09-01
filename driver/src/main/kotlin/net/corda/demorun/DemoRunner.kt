package net.corda.demorun

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.testing.driver.NetworkMapStartStrategy
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver

fun CordformDefinition.clean() {
    System.err.println("Deleting: $driverDirectory")
    driverDirectory.toFile().deleteRecursively()
}

/**
 * Creates and starts all nodes required for the demo.
 */
fun CordformDefinition.runNodes() = driver(
        isDebug = true,
        driverDirectory = driverDirectory,
        networkMapStartStrategy = NetworkMapStartStrategy.Nominated(networkMapNodeName),
        portAllocation = PortAllocation.Incremental(10001)
) {
    setup(this)
    startNodes(nodeConfigurers.map { configurer -> CordformNode().also { configurer.accept(it) } })
    waitForAllNodesToFinish()
}
