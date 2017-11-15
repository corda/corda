@file:JvmName("DemoRunner")

package net.corda.testing.node.internal.demorun

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.JmxPolicy
import net.corda.testing.driver.PortAllocation
import net.corda.testing.node.internal.internalDriver

fun CordformDefinition.clean() {
    System.err.println("Deleting: $nodesDirectory")
    nodesDirectory.toFile().deleteRecursively()
}

/**
 * Deploy the nodes specified in the given [CordformDefinition]. This will block until all the nodes and webservers
 * have terminated.
 */
fun CordformDefinition.deployNodes(extraPackagesToScan: List<String> = emptyList()) {
    runNodes(waitForAllNodesToFinish = true) { }
}

/**
 * Deploy the nodes specified in the given [CordformDefinition] and then execute the given [block] once all the nodes
 * and webservers are up. After execution all these processes will be terminated.
 */
fun CordformDefinition.deployNodesThen(extraPackagesToScan: List<String> = emptyList(), block: () -> Unit) {
    runNodes(waitForAllNodesToFinish = false, block = block)
}

private fun CordformDefinition.runNodes(waitForAllNodesToFinish: Boolean, extraPackagesToScan: List<String> = emptyList(), block: () -> Unit) {
    clean()
    val nodes = nodeConfigurers.map { configurer -> CordformNode().also { configurer.accept(it) } }
    val maxPort = nodes
            .flatMap { listOf(it.p2pAddress, it.rpcAddress, it.webAddress) }
            .mapNotNull { address -> address?.let { NetworkHostAndPort.parse(it).port } }
            .max()!!
    internalDriver(
            isDebug = true,
            jmxPolicy = JmxPolicy(true),
            driverDirectory = nodesDirectory,
            extraCordappPackagesToScan = extraPackagesToScan,
            // Notaries are manually specified in Cordform so we don't want the driver automatically starting any
            notarySpecs = emptyList(),
            // Start from after the largest port used to prevent port clash
            portAllocation = PortAllocation.Incremental(maxPort + 1),
            waitForAllNodesToFinish = waitForAllNodesToFinish
    ) {
        setup(this)
        startCordformNodes(nodes).getOrThrow() // Only proceed once everything is up and running
        println("All nodes and webservers are ready...")
        block()
    }
}
