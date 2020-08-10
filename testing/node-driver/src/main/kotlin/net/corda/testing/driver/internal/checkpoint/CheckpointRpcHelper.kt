package net.corda.testing.driver.internal.checkpoint

import net.corda.core.internal.messaging.CheckpointRPCOps
import net.corda.testing.driver.NodeHandle

object CheckpointRpcHelper {

    interface CloseableCheckpointRPCOps : CheckpointRPCOps, AutoCloseable

    val NodeHandle.checkpointsRpc: CloseableCheckpointRPCOps
        get() {
            val user = rpcUsers.first()

            val rpcConnection = net.corda.client.rpc.internal.RPCClient<CheckpointRPCOps>(rpcAddress)
                    .start(CheckpointRPCOps::class.java, user.username, user.password)
            val proxy = rpcConnection.proxy

            return object : CloseableCheckpointRPCOps, CheckpointRPCOps by proxy {
                override fun close() {
                    rpcConnection.close()
                }
            }
        }
}