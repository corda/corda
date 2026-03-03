package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.identity.Party
import java.security.PublicKey

/**
 * Represents the result of resolving a Party's identity, including any key rotation proofs that may be associated with it.
 * This class provides access to the original key, the current key (after applying any rotations), and the legal name of the party.
 * This is a best effort resolution that relies on the presence of key rotation proofs to determine if two keys are equivalent.
 * Otherwise, the original key is returned as the current key, and the legal name is derived from the original party.
 */
class PartyIdentityResolved(
    originalParty: Party,
    val proofChain: KeyRotationProofChain?
) {

    constructor(
            originalParty: Party,
            proofChain: List<KeyRotationProof>?
    ) : this(
            originalParty,
            if (proofChain != null) KeyRotationProofChain(proofChain) else null
    )

    private val originalKey: PublicKey = originalParty.owningKey
    val originalOrCurrentParty: Party = toOriginalOrCurrentParty(originalParty)

    fun isSameParty(party: Party): Boolean =
            originalOrCurrentParty == party

    fun containsProof(): Boolean = proofChain != null && !proofChain.isEmpty()

    fun getOriginalKey(): PublicKey = originalKey

    fun getLegalName(): String = originalOrCurrentParty.name.toString()

    fun getOwningKey(): PublicKey = if (containsProof()) proofChain!!.currentKey else originalKey

    override fun toString(): String {
        val proofsCount = proofChain?.size() ?: 0
        return "KeyResolutionResult{queriedKey=$originalKey, owningKey=${getOwningKey()}, rotated=${containsProof()}, proofChain=$proofsCount}"
    }

    private fun toOriginalOrCurrentParty(originalParty: Party): Party {
        if (!containsProof()) {
            return originalParty
        }

        return Party(originalParty.name, proofChain!!.currentKey)
    }
}
