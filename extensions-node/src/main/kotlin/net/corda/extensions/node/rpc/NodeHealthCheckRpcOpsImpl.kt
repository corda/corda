package net.corda.extensions.node.rpc

import net.corda.client.rpc.proxy.NodeHealthCheckRpcOps
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.ext.api.NodeInitialContext
import net.corda.ext.api.lifecycle.NodeRpcOps
import com.r3.corda.utils.healthcheck.RuntimeInfoCollector

/**
 * Note: This class is now instantiated by `ServiceLoader` only.
 */
@Suppress("Unused")
class NodeHealthCheckRpcOpsImpl : NodeHealthCheckRpcOps, NodeRpcOps<NodeHealthCheckRpcOps> {

    override val protocolVersion: Int = PLATFORM_VERSION

    override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 1

    override val targetInterface: Class<NodeHealthCheckRpcOps> = NodeHealthCheckRpcOps::class.java

    override fun runtimeInfo(): String {
        return RuntimeInfoCollector.collect()
    }
}