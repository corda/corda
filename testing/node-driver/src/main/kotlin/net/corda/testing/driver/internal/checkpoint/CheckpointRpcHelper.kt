package net.corda.testing.driver.internal.checkpoint

import net.corda.core.internal.messaging.FlowManagerRPCOps
import net.corda.testing.driver.NodeHandle

object CheckpointRpcHelper {

    interface CloseableFlowManagerRPCOps : FlowManagerRPCOps, AutoCloseable

    val NodeHandle.checkpointsRpc: CloseableFlowManagerRPCOps
        get() {
            val user = rpcUsers.first()

            val rpcConnection = net.corda.client.rpc.internal.RPCClient<FlowManagerRPCOps>(rpcAddress)
                    .start(FlowManagerRPCOps::class.java, user.username, user.password)
            val proxy = rpcConnection.proxy

            return object : CloseableFlowManagerRPCOps, FlowManagerRPCOps by proxy {
                override fun close() {
                    rpcConnection.close()
                }
            }
        }
}