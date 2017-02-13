package net.corda.demobench.rpc

import com.google.common.net.HostAndPort
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import net.corda.core.messaging.CordaRPCOps
import net.corda.demobench.loggerFor
import net.corda.demobench.model.NodeConfig
import net.corda.node.services.messaging.CordaRPCClient

class NodeRPC(config: NodeConfig, start: () -> Unit, invoke: (CordaRPCOps) -> Unit): AutoCloseable {
    private val log = loggerFor<NodeRPC>()

    companion object Data {
        private val ONE_SECOND = SECONDS.toMillis(1)
    }

    private val rpcClient = CordaRPCClient(HostAndPort.fromParts("localhost", config.artemisPort), config.ssl)
    private val timer = Timer()

    init {
        val setupTask = object : TimerTask() {
            override fun run() {
                try {
                    rpcClient.start(config.user.getOrElse("user") { "none" } as String,
                                    config.user.getOrElse("password") { "none" } as String)
                    val ops = rpcClient.proxy()

                    // Cancel the "setup" task now that we've created the RPC client.
                    this.cancel()

                    // Run "start-up" task, now that the RPC client is ready.
                    start()

                    // Schedule a new task that will refresh the display once per second.
                    timer.schedule(object: TimerTask() {
                        override fun run() {
                            invoke(ops)
                        }
                    }, 0, ONE_SECOND)
                } catch (e: Exception) {
                    log.warn("Node '{}' not ready yet (Error: {})", config.legalName, e.message)
                }
            }
        }

        // Wait 5 seconds for the node to start, and then poll once per second.
        timer.schedule(setupTask, 5 * ONE_SECOND, ONE_SECOND)
    }

    override fun close() {
        timer.cancel()
        rpcClient.close()
    }

}
