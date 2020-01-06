package net.corda.ext.api

import net.corda.core.CordaInternal
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.CordaTransactionSupport
import net.corda.ext.api.admin.NodeAdmin
import net.corda.ext.api.attachment.AttachmentOperations
import net.corda.ext.api.flow.StateMachineOperations
import net.corda.ext.api.message.MessagingOperations
import net.corda.ext.api.network.NetworkMapOperations

/**
 * Defines a set of tools that will be available for RPC interface implementations to perform useful activity with side effects.
 */
interface NodeServicesContext : NodeInitialContext {
    val serviceHub: ServiceHub
    val database: CordaTransactionSupport
    @CordaInternal
    val nodeAdmin: NodeAdmin
    @CordaInternal
    val networkMapOperations: NetworkMapOperations
    @CordaInternal
    val attachmentOperations: AttachmentOperations
    @CordaInternal
    val stateMachineOperations: StateMachineOperations
    @CordaInternal
    val messagingOperations: MessagingOperations
}