package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.NetworkParametersStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import java.io.ByteArrayOutputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ServicesForResolutionImpl(
        override val identityService: IdentityService,
        override val attachments: AttachmentStorage,
        override val cordappProvider: CordappProvider,
        override val networkParametersStorage: NetworkParametersStorage,
        private val validatedTransactions: TransactionStorage
) : ServicesForResolution {
    override val networkParameters: NetworkParameters get() = networkParametersStorage.lookup(networkParametersStorage.currentHash) ?:
            throw IllegalArgumentException("No current parameters in network parameters storage")

    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this).outputs[stateRef.index]
    }

    @Throws(TransactionResolutionException::class)
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return stateRefs.groupBy { it.txhash }.flatMap {
            val stx = validatedTransactions.getTransaction(it.key) ?: throw TransactionResolutionException(it.key)
            val baseTx = stx.resolveBaseTransaction(this)
            it.value.map { StateAndRef(baseTx.outputs[it.index], it) }
        }.toSet()
    }

    override fun loadContractAttachment(stateRef: StateRef): Attachment? {
        val coreTransaction = validatedTransactions.getTransaction(stateRef.txhash)?.coreTransaction
                ?: throw TransactionResolutionException(stateRef.txhash)
        when (coreTransaction) {
            is WireTransaction -> {
                val transactionState = coreTransaction.outRef<ContractState>(stateRef.index).state
                for (attachmentId in coreTransaction.attachments) {
                    val attachment = attachments.openAttachment(attachmentId)
                    if (attachment is ContractAttachment && transactionState.contract == attachment.contract) {
                        return attachment
                    }
                    if (attachment is ContractAttachment && transactionState.contract in attachment.additionalContracts) {
                        return attachment
                    }
                }
                throw AttachmentResolutionException(stateRef.txhash)
            }
            is ContractUpgradeWireTransaction -> {
                return attachments.openAttachment(coreTransaction.upgradedContractAttachmentId) ?: throw AttachmentResolutionException(stateRef.txhash)
            }
            is NotaryChangeWireTransaction -> {
//                coreTransaction.outRef<ContractState>(stateRef.index).state
//                for (attachmentId in coreTransaction) {
//                    val attachment = attachments.openAttachment(attachmentId)
//                    if (attachment is ContractAttachment && transactionState.contract == attachment.contract) {
//                        return attachment
//                    }
//                }
                val inputStream = ByteArrayOutputStream().apply {
                    ZipOutputStream(this).use {
                        with(it) {
                            putNextEntry(ZipEntry(JarFile.MANIFEST_NAME))
                        }
                    }
                }.toByteArray().inputStream()
                return object : Attachment {
                    override val id get() = throw UnsupportedOperationException()
                    override fun open() = inputStream
                    override val signerKeys get() = throw UnsupportedOperationException()
                    override val signers: List<Party> get() = throw UnsupportedOperationException()
                    override val size: Int = 512
                }
            }
            else -> throw UnsupportedOperationException("Attempting to resolve attachment ${stateRef.index} of a ${coreTransaction.javaClass} transaction. This is not supported.")
        }
    }
}
