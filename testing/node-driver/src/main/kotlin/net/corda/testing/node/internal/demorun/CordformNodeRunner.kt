package net.corda.testing.node.internal.demorun

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.internal.deleteRecursively
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.JmxPolicy
import net.corda.testing.driver.PortAllocation
import net.corda.testing.node.internal.internalDriver

/**
 * Creates a demo runner for this cordform definition
 */
fun CordformDefinition.nodeRunner() = CordformNodeRunner(this)

/**
 * A node runner creates and runs nodes for a given [[CordformDefinition]].
 */
class CordformNodeRunner(private val cordformDefinition: CordformDefinition) {
    private var extraPackagesToScan = emptyList<String>()

    /**
     * Builder method to sets the extra cordapp scan packages
     */
    fun scanPackages(packages: List<String>): CordformNodeRunner {
        extraPackagesToScan = packages
        return this
    }

    fun clean() {
        System.err.println("Deleting: ${cordformDefinition.nodesDirectory}")
        cordformDefinition.nodesDirectory.deleteRecursively()
    }

    /**
     * Deploy the nodes specified in the given [CordformDefinition]. This will block until all the nodes and webservers
     * have terminated.
     */
    fun deployAndRunNodes() {
        runNodes(waitForAllNodesToFinish = true) { }
    }

    /**
     * Deploy the nodes specified in the given [CordformDefinition] and then execute the given [block] once all the nodes
     * and webservers are up. After execution all these processes will be terminated.
     */
    fun deployAndRunNodesAndThen(block: () -> Unit) {
        runNodes(waitForAllNodesToFinish = false, block = block)
    }

    private fun runNodes(waitForAllNodesToFinish: Boolean, block: () -> Unit) {
        clean()
        val nodes = cordformDefinition.nodeConfigurers.map { configurer -> CordformNode().also { configurer.accept(it) } }
        val maxPort = nodes
                .flatMap { listOf(it.p2pAddress, it.rpcAddress, it.webAddress) }
                .mapNotNull { address -> address?.let { NetworkHostAndPort.parse(it).port } }
                .max()!!
        internalDriver(
                jmxPolicy = JmxPolicy(true),
                driverDirectory = cordformDefinition.nodesDirectory,
                extraCordappPackagesToScan = extraPackagesToScan,
                // Notaries are manually specified in Cordform so we don't want the driver automatically starting any
                notarySpecs = emptyList(),
                // Start from after the largest port used to prevent port clash
                portAllocation = PortAllocation.Incremental(maxPort + 1),
                waitForAllNodesToFinish = waitForAllNodesToFinish
        ) {
            cordformDefinition.setup(this)
            startCordformNodes(nodes).getOrThrow() // Only proceed once everything is up and running
            println("All nodes and webservers are ready...")
            block()
        }
    }
}


