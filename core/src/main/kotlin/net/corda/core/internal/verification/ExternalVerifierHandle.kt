package net.corda.core.internal.verification

import net.corda.core.transactions.CoreTransaction

interface ExternalVerifierHandle : AutoCloseable {
    fun verifyTransaction(ctx: CoreTransaction)
}
