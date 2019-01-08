package net.corda.deterministic.verifier

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.toLtxDjvmInternal
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction

@Suppress("MemberVisibilityCanBePrivate")
//TODO the use of deprecated toLedgerTransaction need to be revisited as resolveContractAttachment requires attachments of the transactions which created input states...
//TODO ...to check contract version non downgrade rule, curretly dummy Attachment if not fund is used which sets contract version to '1'
@CordaSerializable
class TransactionVerificationRequest(val wtxToVerify: SerializedBytes<WireTransaction>,
                                     val dependencies: Array<SerializedBytes<WireTransaction>>,
                                     val attachments: Array<ByteArray>,
                                     val networkParameters: SerializedBytes<NetworkParameters>) {
    fun toLedgerTransaction(): LedgerTransaction {
        val deps = dependencies.map { it.deserialize() }.associateBy(WireTransaction::id)
        val attachments = attachments.map { it.deserialize<Attachment>() }
        val attachmentMap = attachments
                .mapNotNull { it as? MockContractAttachment }
                .associateBy(Attachment::id) { ContractAttachment(it, it.contract, uploader = DEPLOYED_CORDAPP_UPLOADER) }
        @Suppress("DEPRECATION")
        return wtxToVerify.deserialize().toLtxDjvmInternal(
                resolveAttachment = { attachmentMap[it] },
                resolveStateRef = { deps[it.txhash]?.outputs?.get(it.index) },
                resolveParameters = { networkParameters.deserialize() }
        )
    }
}
