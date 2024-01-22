package net.corda.core.internal.verification

import net.corda.core.transactions.SignedTransaction

interface ExternalVerifierHandle : AutoCloseable {
    fun verifyTransaction(stx: SignedTransaction, checkSufficientSignatures: Boolean)
}
