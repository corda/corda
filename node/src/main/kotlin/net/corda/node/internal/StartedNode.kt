package net.corda.node.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.CordaPersistence

interface StartedNode<out N : AbstractNode> {
    val node: N
    val services: ServiceHubInternal
    val info: NodeInfo
    val checkpointStorage: CheckpointStorage
    val smm: StateMachineManager
    val attachments: NodeAttachmentService
    val inNodeNetworkMapService: NetworkMapService
    val network: MessagingService
    val database: CordaPersistence
    val rpcOps: CordaRPCOps
    fun dispose() = node.stop()
}
