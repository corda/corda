package net.corda.demorun

import net.corda.node.driver.NetworkMapStartStrategy
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.plugins.cordform.CommonCordform
import net.corda.plugins.cordform.CommonNode

fun CommonCordform.node(configure: CommonNode.() -> Unit) = addNode { commonNode -> commonNode.configure() }

fun CommonCordform.clean() {
    System.err.println("Deleting: $driverDirectory")
    driverDirectory.toFile().deleteRecursively()
}

/**
 * Creates and starts all nodes required for the demo.
 */
fun CommonCordform.runNodes() = driver(
        isDebug = true,
        driverDirectory = driverDirectory,
        networkMapStartStrategy = NetworkMapStartStrategy.Nominated(networkMapNodeName),
        portAllocation = PortAllocation.Incremental(10001)
) {
    setUp(this)
    startNodes(nodeConfigurers.map { configurer -> CommonNode().also { configurer.accept(it) } })
    waitForAllNodesToFinish()
}
