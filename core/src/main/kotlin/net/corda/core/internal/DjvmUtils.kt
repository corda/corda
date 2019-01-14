@file:KeepForDJVM

package net.corda.core.internal

import net.corda.core.KeepForDJVM
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction

fun WireTransaction.toLtxDjvmInternal(
        resolveAttachment: (SecureHash) -> Attachment?,
        resolveStateRef: (StateRef) -> TransactionState<*>?,
        resolveParameters: (SecureHash?) -> NetworkParameters?
): LedgerTransaction {
    return toLtxDjvmInternalBridge(resolveAttachment, resolveStateRef, resolveParameters)
}
