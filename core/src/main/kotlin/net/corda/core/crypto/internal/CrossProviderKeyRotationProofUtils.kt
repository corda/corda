package net.corda.core.crypto.internal

import net.corda.core.contracts.CrossProviderKeyRotationProof
import net.corda.core.crypto.Crypto.doVerify
import net.corda.core.crypto.Crypto.findSignatureScheme
import net.corda.core.crypto.TransactionSignature
import java.security.PublicKey

object CrossProviderKeyRotationProofUtils {

    fun extractPreviousIdentityKeys(signatures: List<TransactionSignature>): Set<PublicKey> {
        val proofs = extractProof(signatures)
        verify(proofs)
        return extractPreviousIdentityKeys(proofs)
    }

    private fun extractProof(signatures: List<TransactionSignature>): Set<CrossProviderKeyRotationProof> {
        return signatures.map { signature -> extractProof(signature) }.flatten().toSet()
    }

    private fun extractProof(signature: TransactionSignature): List<CrossProviderKeyRotationProof> {
        return signature.signatureMetadata.crossProviderKeyRotationProof
    }

    private fun verify(proofs: Collection<CrossProviderKeyRotationProof>) {
        proofs.forEach { proof -> verify(proof) }
    }

    private fun verify(proof: CrossProviderKeyRotationProof) {
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
