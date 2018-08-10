package net.corda.core.internal.notary

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party

/**
 * A service that records input states of the given transaction and provides conflict information
 * if any of the inputs have already been used in another transaction.
 */
interface AsyncUniquenessProvider : UniquenessProvider {
    /** Commits all input states of the given transaction. */
    fun commitAsync(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?, references: List<StateRef>): CordaFuture<Result>

    /** Commits all input states of the given transaction synchronously. Use [commitAsync] for better performance. */
    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?, references: List<StateRef>) {
        val result = commitAsync(states, txId, callerIdentity, requestSignature, timeWindow,references).get()
        if (result is Result.Failure) {
            throw NotaryInternalException(result.error)
        }
    }

    /** The outcome of committing a transaction. */
    sealed class Result {
        /** Indicates that all input states have been committed successfully. */
        object Success : Result()
        /** Indicates that the transaction has not been committed. */
        data class Failure(val error: NotaryError) : Result()
    }
}

