package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.internal.keyrotation.crossprovider.InMemoryProofProvider
import net.corda.core.crypto.internal.keyrotation.crossprovider.KmsProofProvider
import net.corda.core.crypto.internal.keyrotation.crossprovider.ProofProvider
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import java.security.PublicKey
import java.util.Collections

/**
 * Resolves whether two Party instances represent the same legal entity even if their keys have been rotated.
 * This is a best effort resolver that relies on the presence of key rotation proofs to determine if two keys are equivalent.
 */
class PartyIdentityResolver private constructor(private val proofProvider: ProofProvider) {

    constructor(proofsByOriginalKeyMap: Map<PublicKey, KeyRotationProofChain>?) : this(InMemoryProofProvider(proofsByOriginalKeyMap ?: emptyMap()))
    constructor(kms: KeyManagementService) : this(KmsProofProvider(kms))

    companion object {

        fun generateProofChainMap(vararg resolvedParties: PartyIdentityResolved): MutableMap<PublicKey, KeyRotationProofChain> {
            val keyRotationProofs: MutableMap<PublicKey, KeyRotationProofChain> = LinkedHashMap<PublicKey, KeyRotationProofChain>()

            for (resolved in resolvedParties) {
                if (resolved.containsProof()) {
                    keyRotationProofs[resolved.getOriginalKey()] = resolved.proofChain!!
                }
            }
            return Collections.unmodifiableMap(keyRotationProofs)
        }

        // Resolves the given Party to its current legal identity (Party with the latest key) using the provided IdentityService.
        // This does not provide a proof chain, but is useful for cases where only the current Party instance is needed without the need
        // to access the original key or proof details.
        fun resolveToCurrentParty(original: Party, identityService: IdentityService): Party {
            val partyAndCertificate: PartyAndCertificate? = identityService.certificateFromKey(original.owningKey)
            return identityService.wellKnownPartyFromX500Name(partyAndCertificate!!.name)!!
        }
    }

    fun resolve(original: Party): PartyIdentityResolved {
        val proofChain = proofProvider.getProofChain(original.owningKey)
        return PartyIdentityResolved(original, proofChain.takeIf { it.isNotEmpty() })
    }

    /**
     * Returns true when the two parties share the same legal identity (X500 name) and their keys are
     * considered equivalent (direct equality or linked via a valid rotation proof chain).
     */
    fun isSameParty(left: Party, right: Party): Boolean {
        if (left.name != right.name) {
            // Legal identity (X500) must never change. So if there is a mismatch here, we can be sure they are not the same party
            return false
        }

        return areEquivalentKeys(left.owningKey, right.owningKey)
    }

    /**
     * Determines whether the given party is among the required signers for an operation.
     * Checks if any of the provided signer keys corresponds to the party's (possibly rotated) key.
     */
    fun isRequiredSigner(signers: List<PublicKey>, party: Party): Boolean {
        for (signer in signers) if (isSameKey(party.owningKey, signer)) return true
        return false
    }

    /**
     * Key equivalence is considered symmetric: either key1 can be shown to map to key2
     * or key2 can be shown to map to key1 via rotation proofChain.
     */
    private fun areEquivalentKeys(key1: PublicKey, key2: PublicKey): Boolean =
            isSameKey(key1, key2) || isSameKey(key2, key1)

    /**
     * Returns true if candidateKey is the same as originalKey or if there exists a valid proof chain
     * starting from originalKey that links it to candidateKey.
     *
     * If no proof chain is available or validation fails, returns false.
     */
    private fun isSameKey(originalKey: PublicKey, candidateKey: PublicKey): Boolean {
        if (originalKey == candidateKey) {
            return true
        }

        val proofChain = proofProvider.getProofChain(originalKey)
        if (proofChain.isEmpty()) {
            return false
        }

        return proofChain.isRotationValid(originalKey, candidateKey)
    }
}
