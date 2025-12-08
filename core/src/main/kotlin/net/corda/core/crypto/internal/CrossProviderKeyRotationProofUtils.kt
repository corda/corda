package net.corda.core.crypto.internal

import net.corda.core.contracts.CrossProviderKeyRotationProof
import net.corda.core.crypto.Crypto.doVerify
import net.corda.core.crypto.Crypto.findSignatureScheme
import net.corda.core.crypto.TransactionSignature
import java.security.PublicKey

object CrossProviderKeyRotationProofUtils {

    fun extractPreviousIdentityKeys(signatures: List<TransactionSignature>): Set<PublicKey> {
        val proofs = extractProof(signatures)
        return extractPreviousIdentityKeys(proofs)
    }

    private fun extractProof(signatures: List<TransactionSignature>): Set<CrossProviderKeyRotationProof> {
        return signatures.map { signature -> extractProof(signature) }.flatten().toSet()
    }

    private fun extractProof(signature: TransactionSignature): List<CrossProviderKeyRotationProof> {
        val proofChain = signature.signatureMetadata.crossProviderKeyRotationProof
        val proofChainMap = proofChain.associateBy { proof -> proof.publicKeyNew }

        validateProofChainContinuity(signature.by, proofChainMap)

        return proofChain
    }

    private fun validateProofChainContinuity(signingKey: PublicKey, proofChain: Map<PublicKey, CrossProviderKeyRotationProof>) {
        var validProofCount = 0

        // Iterate backwards through the proof chain, verifying each proof
        var activeKey = signingKey
        var currentProof = proofChain[activeKey]
        while(currentProof != null) {
            validateIndividualProof(currentProof)
            activeKey = currentProof.publicKeyOld
            currentProof = proofChain[activeKey]
            validProofCount++
        }

        // Ensure that all proofs in the map are reachable from the current key.
        // This prevents an attacker from injecting unrelated proofs into a valid chain.
        // Example attack scenario:
        //   - Attacker has a valid proof chain A -> B -> C
        //   - Victim has a valid proof chain D -> E -> F
        //   - If the system blindly accepted all proofs, the attacker could create a combined chain:
        //       A -> B -> C, D -> E -> F
        //     and potentially impersonate the victim's keys D, E, F.
        // By verifying that every proof is connected starting from the current key, we guarantee
        // that only the intended, continuous proof chain is accepted.
        check(validProofCount == proofChain.size) {
            "Invalid cross-provider key rotation proof chain: contains unreachable proofs."
        }
    }

    private fun validateIndividualProof(proof: CrossProviderKeyRotationProof) {

        // Verify that the signature is valid: publicKeyOld signed publicKeyNew
        // This means that the old key pair authorises the new key pair to act on its behalf
        doVerify(
                findSignatureScheme(proof.publicKeyOld),
                proof.publicKeyOld,
                proof.signature,
                proof.publicKeyNew.encoded
        )
    }

    private fun extractPreviousIdentityKeys(proofs: Collection<CrossProviderKeyRotationProof>): Set<PublicKey> {
        return proofs.map { proof -> proof.publicKeyOld }.toSet()
    }
}
