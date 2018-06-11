package net.corda.deterministic.common

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.TEST_UPLOADER
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction

@Suppress("MemberVisibilityCanBePrivate")
@CordaSerializable
class TransactionVerificationRequest(val wtxToVerify: SerializedBytes<WireTransaction>,
                                     val dependencies: Array<SerializedBytes<WireTransaction>>,
                                     val attachments: Array<ByteArray>) {
    fun toLedgerTransaction(): LedgerTransaction {
        val deps = dependencies.map { it.deserialize() }.associateBy(WireTransaction::id)
        val attachments = attachments.map { it.deserialize<Attachment>() }
        val attachmentMap = attachments.mapNotNull { it as? MockContractAttachment }
                .associateBy(Attachment::id, { ContractAttachment(it, it.contract, uploader=TEST_UPLOADER) })
        val contractAttachmentMap = emptyMap<ContractClassName, ContractAttachment>()
        @Suppress("DEPRECATION")
        return wtxToVerify.deserialize().toLedgerTransaction(
            resolveIdentity = { null },
            resolveAttachment = { attachmentMap[it] },
            resolveStateRef = { deps[it.txhash]?.outputs?.get(it.index) },
            resolveContractAttachment = { contractAttachmentMap[it.contract]?.id }
        )
    }
}
