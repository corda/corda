package net.corda.core.internal.verification

import net.corda.core.transactions.FullTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.Try.Failure
import net.corda.core.utilities.Try.Success

sealed class VerificationResult {
    /**
     * The in-process result for the current version of the transcaction.
     */
    abstract val inProcessResult: Try<FullTransaction?>?

    /**
     * The external verifier results for the legacy versions of the transaction, keyed by the legacy [ContractGeneration] verified.
     */
    abstract val externalResults: Map<ContractGeneration, Try<Unit>>

    abstract fun enforceSuccess(): FullTransaction?


    data class InProcess(override val inProcessResult: Try<FullTransaction?>) : VerificationResult() {
        override val externalResults: Map<ContractGeneration, Try<Unit>>
            get() = emptyMap()

        override fun enforceSuccess(): FullTransaction? = inProcessResult.getOrThrow()
    }

    data class External(override val externalResults: Map<ContractGeneration, Try<Unit>>) : VerificationResult() {
        constructor(generation: ContractGeneration, externalResult: Try<Unit>) : this(mapOf(generation to externalResult))

        init {
            require(externalResults.isNotEmpty()) { "No external results" }
        }

        override val inProcessResult: Try<FullTransaction?>?
            get() = null

        override fun enforceSuccess(): FullTransaction? {
            // The newest legacy version is taken as the primary result, which any older versions must agree with.
            val newest = ContractGeneration.values().last { it in externalResults }
            enforceConsistent("Legacy $newest", externalResults.getValue(newest), externalResults - newest)
            // We could create a LedgerTransaction here, and except for calling `verify()`, it would be valid to use. However, it's best
            // we let the caller deal with that, since we can't prevent them from calling it.
            return null
        }
    }

    data class InProcessAndExternal(
            override val inProcessResult: Try<LedgerTransaction>,
            override val externalResults: Map<ContractGeneration, Try<Unit>>
    ) : VerificationResult() {
        constructor(inProcessResult: Try<LedgerTransaction>, generation: ContractGeneration, externalResult: Try<Unit>)
                : this(inProcessResult, mapOf(generation to externalResult))

        init {
            require(externalResults.isNotEmpty()) { "No external results" }
        }

        override fun enforceSuccess(): FullTransaction {
            enforceConsistent("Current", inProcessResult, externalResults)
            return inProcessResult.getOrThrow()
        }
    }

    /**
     * All the versions of a transaction must agree. If the [primaryResult] is a success then all the [legacyResults] must be as well, and
     * if it failed then the legacy versions must also fail, in which case the primary exception is thrown.
     */
    protected fun enforceConsistent(primaryName: String, primaryResult: Try<*>, legacyResults: Map<ContractGeneration, Try<Unit>>) {
        val legacyFailures = LinkedHashMap<ContractGeneration, Throwable>()
        for ((generation, result) in legacyResults) {
            if (result is Failure) legacyFailures[generation] = result.exception
        }
        when (primaryResult) {
            is Success -> if (legacyFailures.isNotEmpty()) {
                val exceptions = legacyFailures.values.toList()
                throw IllegalStateException(
                        "$primaryName version of transaction verified, but legacy version failed to verify (in external verifier): " +
                                "${legacyFailures.keys}",
                        exceptions[0]
                ).apply { exceptions.drop(1).forEach { addSuppressed(it) } }
            }
            is Failure -> {
                if (legacyFailures.size == legacyResults.size) {
                    // Every version failed, and so the transaction is consistently invalid.
                    throw primaryResult.exception.apply { legacyFailures.values.forEach { addSuppressed(it) } }
                }
                throw IllegalStateException(
                        "$primaryName version of transaction failed to verify, but legacy version did verify (in external verifier): " +
                                "${legacyResults.keys - legacyFailures.keys}",
                        primaryResult.exception
                ).apply { legacyFailures.values.forEach { addSuppressed(it) } }
            }
        }
    }
}
