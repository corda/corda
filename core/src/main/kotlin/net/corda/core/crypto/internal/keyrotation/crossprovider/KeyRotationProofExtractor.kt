package net.corda.core.crypto.internal.keyrotation.crossprovider

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.TransactionSignature
import java.security.PublicKey

object KeyRotationProofExtractor {

    fun extractPreviousIdentityKeys(signatures: List<TransactionSignature>): Set<PublicKey> {
        val proofs = extractProofsFromSignature(signatures)
        return extractPreviousIdentityKeys(proofs)
    }

    private fun extractProofsFromSignature(signatures: List<TransactionSignature>): Set<KeyRotationProof> {
        return signatures.flatMap { signature -> extractProofsFromSignature(signature) }.toSet()
    }

    private fun extractProofsFromSignature(signature: TransactionSignature): List<KeyRotationProof> {
        val proofChain = signature.signatureMetadata.crossProviderKeyRotationProof
        val newPublicKeyToProofMap = proofChain.associateBy { proof -> proof.publicKeyNew }

        verifyContinuousProofChain(signature.by, newPublicKeyToProofMap)

        return proofChain
    }

    private fun verifyContinuousProofChain(signingKey: PublicKey, newPublicKeyToProofMap: Map<PublicKey, KeyRotationProof>) {

        // Iterate backwards through the proof chain, verifying each proof
        var activeKey = signingKey
        val keysSeenSoFar = mutableSetOf<PublicKey>()
        var currentProof = newPublicKeyToProofMap[activeKey]
        while(currentProof != null) {
            if(!keysSeenSoFar.add(activeKey)) {
                // Detected a cycle in the proof chain
                throw IllegalArgumentException("Invalid cross-provider key rotation proof chain: Contains a cycle.")
            }

            verifyProof(currentProof)
            activeKey = currentProof.publicKeyOld
            currentProof = newPublicKeyToProofMap[activeKey]
        }

        // Ensure that all proofChain in the map are reachable from the signing key.
        // This prevents an attacker from injecting unrelated proofChain into a valid chain.
        // Example attack scenario:
        //   - Attacker has a valid proof chain A -> B -> C
        //   - Victim has a valid proof chain D -> E -> F
        //   - If the system blindly accepted all proofChain, the attacker could create a combined chain:
        //       A -> B -> C, D -> E -> F
        //     and potentially impersonate the victim's keys D, E, F.
        // By verifying that every proof is connected starting from the signing key, we guarantee
        // that only the intended, continuous proof chain is accepted.
        check(keysSeenSoFar.size == newPublicKeyToProofMap.size) {
            "Invalid cross-provider key rotation proof chain: contains unreachable proofChain."
        }
    }

    private fun verifyProof(proof: KeyRotationProof) {

        // Verify that the signature is valid: publicKeyOld signed publicKeyNew
        // This means that the old key pair authorises the new key pair to act on its behalf
        Crypto.doVerify(
                Crypto.findSignatureScheme(proof.publicKeyOld),
                proof.publicKeyOld,
                proof.signature,
                proof.publicKeyNew.encoded
        )
    }

    private fun extractPreviousIdentityKeys(proofs: Collection<KeyRotationProof>): Set<PublicKey> {
        return proofs.map { proof -> proof.publicKeyOld }.toSet()
    }
}
