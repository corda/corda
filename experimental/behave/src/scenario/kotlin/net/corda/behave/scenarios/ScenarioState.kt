/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios

import cucumber.api.java.After
import net.corda.behave.logging.getLogger
import net.corda.behave.network.Network
import net.corda.behave.node.Distribution
import net.corda.behave.node.Node
import net.corda.core.messaging.CordaRPCOps
import org.assertj.core.api.Assertions.assertThat
import java.time.Duration

class ScenarioState {

    private val log = getLogger<ScenarioState>()

    private val nodes = mutableListOf<Node.Builder>()

    private var network: Network? = null

    fun fail(message: String) {
        error<Unit>(message)
    }

    fun<T> error(message: String, ex: Throwable? = null): T {
        this.network?.signalFailure(message, ex)
        if (ex != null) {
            throw Exception(message, ex)
        } else {
            throw Exception(message)
        }
    }

    fun node(name: String): Node {
        val network = network ?: error("Network is not running")
        return network[nodeName(name)] ?: error("Node '$name' not found")
    }

    fun nodeBuilder(name: String): Node.Builder {
        return nodes.firstOrNull { it.name == nodeName(name) } ?: newNode(name)
    }

    fun ensureNetworkIsRunning(timeout: Duration? = null) {
        if (network != null) {
            // Network is already running
            return
        }

        // Corda Network will be configured as R3 Corda (with Doorman/NMS) if any Node uses an R3.Corda distribution
        val r3CordaNode = nodes.find { it.networkType == Distribution.Type.R3_CORDA }
        val networkType = if (r3CordaNode != null) Distribution.Type.R3_CORDA else Distribution.Type.CORDA
        log.info("Corda network type: $networkType")

        val networkBuilder = Network.new(networkType)
        for (node in nodes) {
            networkBuilder.addNode(node.withNetworkType(networkType))
        }
        network = networkBuilder.generate()
        network?.start()
        assertThat(network?.waitUntilRunning(timeout)).isTrue()
    }

    inline fun <T> withNetwork(action: ScenarioState.() -> T): T {
        ensureNetworkIsRunning()
        return action()
    }

    inline fun <T> withClient(nodeName: String, crossinline action: (CordaRPCOps) -> T): T {
        withNetwork {
            return node(nodeName).rpc {
                action(it)
            }
        }
    }

    inline fun <T> withClientProxy(nodeName: String, crossinline action: (CordaRPCOps) -> T): T {
        withNetwork {
            return node(nodeName).http {
                action(it)
            }
        }
    }

    @After
    fun stopNetwork() {
        val network = network ?: return
        for (node in network) {
            val matches = node.logOutput.find("\\[ERR")
            if (matches.any()) {
                fail("Found errors in the log for node '${node.config.name}': ${matches.first().filename}")
            }
        }
        network.stop()
    }

    private fun nodeName(name: String) = "$name"

    private fun newNode(name: String): Node.Builder {
        val builder = Node.new()
                .withName(nodeName(name))
        nodes.add(builder)
        return builder
    }

}