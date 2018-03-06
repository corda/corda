/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.rpc

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.demobench.model.NodeConfigWrapper
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

class NodeRPC(config: NodeConfigWrapper, start: (NodeConfigWrapper, CordaRPCOps) -> Unit, invoke: (CordaRPCOps) -> Unit) : AutoCloseable {
    private companion object {
        private val log = contextLogger()
        val oneSecond = SECONDS.toMillis(1)
    }

    private val rpcClient = CordaRPCClient(NetworkHostAndPort("localhost", config.nodeConfig.rpcAddress.port))
    private var rpcConnection: CordaRPCConnection? = null
    private val timer = Timer()

    init {
        val setupTask = object : TimerTask() {
            override fun run() {
                try {
                    val user = config.nodeConfig.rpcUsers[0]
                    val connection = rpcClient.start(user.username, user.password)
                    rpcConnection = connection
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
                    log.warn("Node '{}' not ready yet (Error: {})", config.nodeConfig.myLegalName, e.message)
                }
            }
        }

        // Wait 5 seconds for the node to start, and then poll once per second.
        timer.schedule(setupTask, 5 * oneSecond, oneSecond)
    }

    override fun close() {
        timer.cancel()
        try {
            rpcConnection?.forceClose()
        } catch (e: Exception) {
            log.error("Failed to close RPC connection (Error: {})", e.message)
        }
    }

}
