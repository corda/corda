package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.transactions.LedgerTransaction

@DeleteForDJVM
interface TransactionVerifierServiceInternal {
    /**
     * Verifies the [transaction] but adds some [extraAttachments] to the classpath.
     * Required for transactions built with Corda 3.x that might miss some dependencies due to a bug in that version.
     */
    fun verify(transaction: LedgerTransaction, extraAttachments: List<Attachment> ): CordaFuture<*>
}

/**
 * Defined here for visibility reasons.
 */
fun LedgerTransaction.verify(extraAttachments: List<Attachment>) = this.verifyInternal(extraAttachments)