package net.corda.testing.driver.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.node.User
import rx.Observable
import java.nio.file.Path

interface NodeHandleInternal : NodeHandle {
    val configuration: NodeConfiguration
    val useHTTPS: Boolean
    val webAddress: NetworkHostAndPort
    override val p2pAddress: NetworkHostAndPort get() = configuration.p2pAddress
    override val rpcAddress: NetworkHostAndPort get() = configuration.rpcOptions.address
    override val rpcAdminAddress: NetworkHostAndPort get() = configuration.rpcOptions.adminAddress
    override val baseDirectory: Path get() = configuration.baseDirectory
}

data class OutOfProcessImpl(
        override val nodeInfo: NodeInfo,
        override val rpc: CordaRPCOps,
        override val configuration: NodeConfiguration,
        override val webAddress: NetworkHostAndPort,
        override val useHTTPS: Boolean,
        val debugPort: Int?,
        override val process: Process,
        private val onStopCallback: () -> Unit
) : OutOfProcess, NodeHandleInternal {
    override val rpcUsers: List<User> = configuration.rpcUsers.map { User(it.username, it.password, it.permissions) }
    override fun stop() {
        with(process) {
            destroy()
            waitFor()
        }
        onStopCallback()
    }

    override fun close() = stop()
}

data class InProcessImpl(
        override val nodeInfo: NodeInfo,
        override val rpc: CordaRPCOps,
        override val configuration: NodeConfiguration,
        override val webAddress: NetworkHostAndPort,
        override val useHTTPS: Boolean,
        private val nodeThread: Thread,
        private val onStopCallback: () -> Unit,
        private val node: StartedNode<Node>
) : InProcess, NodeHandleInternal {
    val database: CordaPersistence = node.database
    override val services: StartedNodeServices get() = node.services
    override val rpcUsers: List<User> = configuration.rpcUsers.map { User(it.username, it.password, it.permissions) }
    override fun stop() {
        node.dispose()
        with(nodeThread) {
            interrupt()
            join()
        }
        onStopCallback()
    }

    override fun close() = stop()
    override fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>): Observable<T> = node.registerInitiatedFlow(initiatedFlowClass)
}

val InProcess.internalServices: StartedNodeServices get() = services as StartedNodeServices
