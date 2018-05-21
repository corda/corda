package net.corda.node.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.rpcContext
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.nodeapi.internal.persistence.CordaPersistence

/**
 * Implementation of [CordaRPCOps] that checks authorisation.
 */
class SecureCordaRPCOps(services: ServiceHubInternal,
                        smm: StateMachineManager,
                        database: CordaPersistence,
                        flowStarter: FlowStarter,
                        shutdownNode: () -> Unit,
                        val unsafe: CordaRPCOps = CordaRPCOpsImpl(services, smm, database, flowStarter, shutdownNode)) : CordaRPCOps by RpcAuthorisationProxy(unsafe, ::rpcContext) {

    /**
     * Returns the RPC protocol version, which is the same the node's Platform Version. Exists since version 1 so guaranteed
     * to be present.
     */
    override val protocolVersion: Int get() = unsafe.nodeInfo().platformVersion
}