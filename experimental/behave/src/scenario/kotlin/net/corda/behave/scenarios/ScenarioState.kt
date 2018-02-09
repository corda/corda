package net.corda.behave.scenarios

import net.corda.behave.logging.getLogger
import net.corda.behave.network.Network
import net.corda.behave.node.Node
import net.corda.behave.seconds
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThat

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

    fun ensureNetworkIsRunning() {
        if (network != null) {
            // Network is already running
            return
        }
        val networkBuilder = Network.new()
        for (node in nodes) {
            networkBuilder.addNode(node)
        }
        network = networkBuilder.generate()
        network?.start()
        assertThat(network?.waitUntilRunning()).isTrue()
    }

    fun withNetwork(action: ScenarioState.() -> Unit) {
        ensureNetworkIsRunning()
        action()
    }

    fun <T> withClient(nodeName: String, action: (CordaRPCOps) -> T): T {
        var result: T? = null
        withNetwork {
            val node = node(nodeName)
            val user = node.config.users.first()
            val address = node.config.nodeInterface
            val targetHost = NetworkHostAndPort(address.host, address.rpcPort)
            val config = CordaRPCClientConfiguration(
                    connectionMaxRetryInterval = 10.seconds
            )
            log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
            CordaRPCClient(targetHost, config).use(user.username, user.password) {
                log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                val client = it.proxy
                result = action(client)
            }
        }
        return result ?: error("Failed to run RPC action")
    }

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

    private fun nodeName(name: String) = "Entity$name"

    private fun newNode(name: String): Node.Builder {
        val builder = Node.new()
                .withName(nodeName(name))
        nodes.add(builder)
        return builder
    }

}