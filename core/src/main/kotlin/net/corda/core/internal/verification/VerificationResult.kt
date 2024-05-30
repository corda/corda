package net.corda.core.internal.verification

import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.Try.Failure
import net.corda.core.utilities.Try.Success

sealed class VerificationResult {
    /**
     * The in-process result for the current version of the transcaction.
     */
    abstract val inProcessResult: Try<LedgerTransaction?>?

    /**
     * The external verifier result for the legacy version of the transaction.
     */
    abstract val externalResult: Try<Unit>?

    abstract fun enforceSuccess(): LedgerTransaction?


    data class InProcess(override val inProcessResult: Try<LedgerTransaction?>) : VerificationResult() {
        override val externalResult: Try<Unit>?
            get() = null

        override fun enforceSuccess(): LedgerTransaction? = inProcessResult.getOrThrow()
    }

    data class External(override val externalResult: Try<Unit>) : VerificationResult() {
        override val inProcessResult: Try<LedgerTransaction?>?
            get() = null

        override fun enforceSuccess(): LedgerTransaction? {
            externalResult.getOrThrow()
            // We could create a LedgerTransaction here, and except for calling `verify()`, it would be valid to use. However, it's best
            // we let the caller deal with that, since we can't prevent them from calling it.
            return null
        }
    }

    data class InProcessAndExternal(
            override val inProcessResult: Try<LedgerTransaction>,
            override val externalResult: Try<Unit>
    ) : VerificationResult() {
        override fun enforceSuccess(): LedgerTransaction {
            return when (externalResult) {
                is Success -> when (inProcessResult) {
                    is Success -> inProcessResult.value
                    is Failure -> throw IllegalStateException(
                            "Current version of transaction failed to verify, but legacy version did verify (in external verifier)",
                            inProcessResult.exception
                    )
                }
                is Failure -> throw when (inProcessResult) {
                    is Success -> IllegalStateException(
                            "Current version of transaction verified, but legacy version failed to verify (in external verifier)",
                            externalResult.exception
                    )
                    is Failure -> inProcessResult.exception.apply { addSuppressed(externalResult.exception) }
                }
            }
        }
    }
}
