@file:JvmName("DemoRunner")

package net.corda.testing.internal.demorun

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.DriverDSLImpl
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver

fun CordformDefinition.clean() {
    System.err.println("Deleting: $nodesDirectory")
    nodesDirectory.toFile().deleteRecursively()
}

/**
 * Deploy the nodes specified in the given [CordformDefinition]. This will block until all the nodes and webservers
 * have terminated.
 */
fun CordformDefinition.deployNodes() {
    runNodes(waitForAllNodesToFinish = true) { }
}

/**
 * Deploy the nodes specified in the given [CordformDefinition] and then execute the given [block] once all the nodes
 * and webservers are up. After execution all these processes will be terminated.
 */
fun CordformDefinition.deployNodesThen(block: () -> Unit) {
    runNodes(waitForAllNodesToFinish = false, block = block)
}

private fun CordformDefinition.runNodes(waitForAllNodesToFinish: Boolean, block: () -> Unit) {
    val nodes = nodeConfigurers.map { configurer -> CordformNode().also { configurer.accept(it) } }
    val maxPort = nodes
            .flatMap { listOf(it.p2pAddress, it.rpcAddress, it.webAddress) }
            .mapNotNull { address -> address?.let { NetworkHostAndPort.parse(it).port } }
            .max()!!
    driver(
            isDebug = true,
            driverDirectory = nodesDirectory,
            extraCordappPackagesToScan = cordappPackages,
            // Notaries are manually specified in Cordform so we don't want the driver automatically starting any
            notarySpecs = emptyList(),
            // Start from after the largest port used to prevent port clash
            portAllocation = PortAllocation.Incremental(maxPort + 1),
            waitForAllNodesToFinish = waitForAllNodesToFinish
    ) {
        this as DriverDSLImpl  // access internal API
        setup(this)
        nodes.map {
            val startedNode = startCordformNode(it)
            if (it.webAddress != null) {
                // Start a webserver if an address for it was specified
                startedNode.flatMap { startWebserver(it) }
            } else {
                startedNode
            }
        }.transpose().getOrThrow()  // Only proceed once everything is up and running
        println("All nodes and webservers are ready...")
        block()
    }
}
