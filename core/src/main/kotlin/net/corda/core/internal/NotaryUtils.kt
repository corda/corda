package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.NotarisationResponse
import net.corda.core.identity.Party

/**
 * Checks that there are sufficient signatures to satisfy the notary signing requirement and validates the signatures
 * against the given transaction id.
 */
fun NotarisationResponse.validateSignatures(txId: SecureHash, notary: Party) {
    val signingKeys = signatures.map { it.by }
    require(notary.owningKey.isFulfilledBy(signingKeys)) { "Insufficient signatures to fulfill the notary signing requirement for $notary" }
    signatures.forEach { it.verify(txId) }
}