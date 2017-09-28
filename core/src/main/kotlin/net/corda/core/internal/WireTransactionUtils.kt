package net.corda.core.internal

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import java.security.PublicKey

fun WireTransaction.toLedgerTransaction(
        resolveIdentity: (PublicKey) -> Party?,
        resolveAttachment: (SecureHash) -> Attachment?,
        resolveStateRef: (StateRef) -> TransactionState<*>?,
        resolveContractAttachment: (TransactionState<ContractState>) -> AttachmentId?
): LedgerTransaction {
    return internalToLedgerTransaction(resolveIdentity, resolveAttachment, resolveStateRef, resolveContractAttachment)
}