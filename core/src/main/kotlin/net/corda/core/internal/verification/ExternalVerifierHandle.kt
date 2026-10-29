package net.corda.core.internal.verification

import net.corda.core.transactions.CoreTransaction

interface ExternalVerifierHandle : AutoCloseable {
    /**
     * Verify the given transaction using the external verifier for the given legacy [ContractGeneration]. Only that generation's
     * attachments are used.
     */
    fun verifyTransaction(ctx: CoreTransaction, generation: ContractGeneration)
}
