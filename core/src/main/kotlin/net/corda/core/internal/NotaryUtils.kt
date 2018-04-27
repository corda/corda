package net.corda.core.internal

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.NotarisationResponse
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import java.time.Instant

/**
 * Checks that there are sufficient signatures to satisfy the notary signing requirement and validates the signatures
 * against the given transaction id.
 */
fun NotarisationResponse.validateSignatures(txId: SecureHash, notary: Party) {
    val signingKeys = signatures.map { it.by }
    require(notary.owningKey.isFulfilledBy(signingKeys)) { "Insufficient signatures to fulfill the notary signing requirement for $notary" }
    signatures.forEach { it.verify(txId) }
}

/** Checks if the provided states were used as inputs in the specified transaction. */
fun isConsumedByTheSameTx(txIdHash: SecureHash, consumedStates: Map<StateRef, StateConsumptionDetails>): Boolean {
    val conflicts = consumedStates.filter { (_, cause) ->
        cause.hashOfTransactionId != txIdHash
    }
    return conflicts.isEmpty()
}

/** Returns [NotaryError.TimeWindowInvalid] if [currentTime] is outside the [timeWindow], and *null* otherwise. */
fun validateTimeWindow(currentTime: Instant, timeWindow: TimeWindow?): NotaryError.TimeWindowInvalid? {
    return if (timeWindow != null && currentTime !in timeWindow) {
        NotaryError.TimeWindowInvalid(currentTime, timeWindow)
    } else null
}