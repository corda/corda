package net.corda.core.internal

import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.StateLoader
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.transactions.*

/**
 * Resolves the underlying base transaction and then returns it, handling any special case transactions such as
 * [NotaryChangeWireTransaction].
 */
fun SignedTransaction.resolveBaseTransaction(stateLoader: StateLoader, attachments: AttachmentStorage): BaseTransaction {
    return when (coreTransaction) {
        is NotaryChangeWireTransaction -> resolveNotaryChangeTransaction(stateLoader)
        is ContractUpgradeWireTransaction -> resolveContractUpgradeTransaction(stateLoader, attachments)
        is WireTransaction -> this.tx
        is FilteredTransaction -> throw IllegalStateException("Persistence of filtered transactions is not supported.")
        else -> throw IllegalStateException("Unknown transaction type ${coreTransaction::class.qualifiedName}")
    }
}

/**
 * If the enclosed [coreTransaction] is a [ContractUpgradeWireTransaction], loads the input states and resolves it to a
 * [ContractUpgradeLedgerTransaction] so the signatures can be verified.
 */
fun SignedTransaction.resolveContractUpgradeTransaction(stateLoader: StateLoader, attachments: AttachmentStorage): ContractUpgradeLedgerTransaction {
    val ctx = coreTransaction as? ContractUpgradeWireTransaction
            ?: throw IllegalStateException("Expected a ${ContractUpgradeWireTransaction::class.simpleName} but found ${coreTransaction::class.simpleName}")
    return ctx.resolve(stateLoader, attachments, sigs)
}

/** Resolves input states and contract attachments, and builds a ContractUpgradeLedgerTransaction. */
fun ContractUpgradeWireTransaction.resolve(stateLoader: StateLoader, attachments: AttachmentStorage, sigs: List<TransactionSignature>): ContractUpgradeLedgerTransaction {
    val resolvedInputs = inputs.map { ref ->
        stateLoader.loadState(ref).let { StateAndRef(it, ref) }
    }
    val legacyContractClassName = resolvedInputs.first().state.contract
    val legacyContractAttachment = attachments.openAttachment(legacyContractAttachmentId)
            ?: throw AttachmentResolutionException(legacyContractAttachmentId)
    val upgradedContractAttachment = attachments.openAttachment(upgradedContractAttachmentId)
            ?: throw AttachmentResolutionException(upgradedContractAttachmentId)

    return ContractUpgradeLedgerTransaction(
            resolvedInputs,
            notary,
            ContractAttachment(legacyContractAttachment, legacyContractClassName),
            ContractAttachment(upgradedContractAttachment, upgradeContractClassName),
            id,
            privacySalt,
            sigs
    )
}