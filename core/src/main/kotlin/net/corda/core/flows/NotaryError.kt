package net.corda.core.flows

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Exception thrown by the notary service if any issues are encountered while trying to commit a transaction. The
 * underlying [error] specifies the cause of failure.
 */
class NotaryException(
        /** Cause of notarisation failure. */
        val error: NotaryError,
        /** Id of the transaction to be notarised. Can be _null_ if an error occurred before the id could be resolved. */
        val txId: SecureHash? = null
) : FlowException("Unable to notarise transaction ${txId ?: "<Unknown>"} : $error")

/** Specifies the cause for notarisation request failure. */
@CordaSerializable
sealed class NotaryError {
    /** Occurs when one or more input states have already been consumed by another transaction. */
    data class Conflict(
            /** Id of the transaction that was attempted to be notarised. */
            val txId: SecureHash,
            /** Specifies which states have already been consumed in another transaction. */
            val consumedStates: Map<StateRef, StateConsumptionDetails>
    ) : NotaryError() {
        override fun toString() = "Conflict notarising transaction $txId. " +
                "Input states have been used in another transactions, count: ${consumedStates.size}, " +
                "content: ${consumedStates.asSequence().joinToString(limit = 5) { it.key.toString() + "->" + it.value }}"
    }

    /** Occurs when time specified in the [TimeWindow] command is outside the allowed tolerance. */
    data class TimeWindowInvalid(val currentTime: Instant, val txTimeWindow: TimeWindow) : NotaryError() {
        override fun toString() = "Current time $currentTime is outside the time bounds specified by the transaction: $txTimeWindow"

        companion object {
            @JvmField
            @Deprecated("Here only for binary compatibility purposes, do not use.")
            val INSTANCE = TimeWindowInvalid(Instant.EPOCH, TimeWindow.fromOnly(Instant.EPOCH))
        }
    }

    /** Occurs when the provided transaction fails to verify. */
    data class TransactionInvalid(val cause: Throwable) : NotaryError() {
        override fun toString() = cause.toString()
    }

    /** Occurs when the transaction sent for notarisation is assigned to a different notary identity. */
    object WrongNotary : NotaryError()

    /** Occurs when the notarisation request signature does not verify for the provided transaction. */
    data class RequestSignatureInvalid(val cause: Throwable) : NotaryError() {
        override fun toString() = "Request signature invalid: $cause"
    }

    /** Occurs when the notary service encounters an unexpected issue or becomes temporarily unavailable. */
    data class General(val cause: Throwable) : NotaryError() {
        override fun toString() = cause.toString()
    }
}

/** Contains information about the consuming transaction for a particular state. */
// TODO: include notary timestamp?
@CordaSerializable
data class StateConsumptionDetails(
        /**
         * Hash of the consuming transaction id.
         *
         * Note that this is NOT the transaction id itself – revealing it could lead to privacy leaks.
         */
        val hashOfTransactionId: SecureHash
)