package net.corda.demorun

import net.corda.node.driver.NetworkMapStartStrategy
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode

fun CordformDefinition.node(configure: CordformNode.() -> Unit) = addNode { cordformNode -> cordformNode.configure() }

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
