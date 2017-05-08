package net.corda.demobench.rpc

import com.google.common.net.HostAndPort
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import net.corda.demobench.model.NodeConfig
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

class NodeRPC(config: NodeConfig, start: (NodeConfig, CordaRPCOps) -> Unit, invoke: (CordaRPCOps) -> Unit) : AutoCloseable {

    private companion object {
        val log = loggerFor<NodeRPC>()
        val oneSecond = SECONDS.toMillis(1)
    }

    private val rpcClient = CordaRPCClient(HostAndPort.fromParts("localhost", config.rpcPort))
    private val timer = Timer()
    private val connections = Collections.synchronizedCollection(ArrayList<CordaRPCConnection>())

    init {
        val setupTask = object : TimerTask() {
            override fun run() {
                try {
                    val user = config.users.elementAt(0)
                    val connection = rpcClient.start(user.username, user.password)
                    connections.add(connection)
                    val ops = connection.proxy

                    // Cancel the "setup" task now that we've created the RPC client.
                    this.cancel()

                    // Run "start-up" task, now that the RPC client is ready.
                    start(config, ops)

                    // Schedule a new task that will refresh the display once per second.
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            invoke(ops)
                        }
                    }, 0, oneSecond)
                } catch (e: Exception) {
                    log.warn("Node '{}' not ready yet (Error: {})", config.legalName, e.message)
                }
            }
        }

        // Wait 5 seconds for the node to start, and then poll once per second.
        timer.schedule(setupTask, 5 * oneSecond, oneSecond)
    }

    override fun close() {
        timer.cancel()
        connections.forEach(CordaRPCConnection::close)
    }

}
