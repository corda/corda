package net.corda.core.internal.notary

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import java.time.Duration

typealias SigningFunction = (SecureHash, Party?) -> TransactionSignature

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
            references: List<StateRef> = emptyList(),
            notary: Party? = null
    ): CordaFuture<Result>

    /**
     * Estimated time of request processing. A uniqueness provider that is aware of their own throughput can return
     * an estimate how long requests will be queued before they can be processed. Notary services use this information
     * to potentially update clients with an expected wait time in order to avoid spamming by retries when the notary
     * gets busy.
     *
     * @param numStates The number of states (input + reference) in the new request, to be added to the pending count.
     */
    fun getEta(numStates: Int): Duration {
        return NotaryServiceFlow.defaultEstimatedWaitTime
    }

    /** The outcome of committing and signing a transaction. */
    sealed class Result {
        /** Indicates that all input states have been committed successfully. */
        data class Success(val signature: TransactionSignature) : Result()

        /** Indicates that the transaction has not been committed. */
        data class Failure(val error: NotaryError) : Result()
    }
}