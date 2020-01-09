package net.corda.client.rpc.proxy

import net.corda.client.rpc.internal.security.READ_ONLY
import net.corda.client.rpc.internal.security.RpcPermissionGroup
import net.corda.core.messaging.RPCOps

/**
 * RPC client side interface for health check data retrieval.
 */
interface NodeHealthCheckRpcOps : RPCOps {

    /**
     * Produces runtime info for a running node as string.
     */
    @RpcPermissionGroup(READ_ONLY)
    fun runtimeInfo() : String
}