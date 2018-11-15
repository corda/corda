package net.corda.core.internal.notary

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import net.corda.core.utilities.seconds
import java.time.Duration

/**
 * A service that records input states of the given transaction and provides conflict information
 * if any of the inputs have already been used in another transaction.
 */
interface UniquenessProvider {
    /** Commits all input states of the given transaction. */
    fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ): CordaFuture<Result>

    // Estimated time of request processing.
    fun eta(): Duration {
       return 30.seconds
    }

    /** The outcome of committing a transaction. */
    sealed class Result {
        /** Indicates that all input states have been committed successfully. */
        object Success : Result()
        /** Indicates that the transaction has not been committed. */
        data class Failure(val error: NotaryError) : Result()
    }
}
