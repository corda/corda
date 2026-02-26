package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.TransactionSignature
import java.security.InvalidKeyException
import java.security.PublicKey
import kotlin.collections.forEach

fun List<TransactionSignature>.ensureKeyRotationProofChainValid() {
    forEach { sig ->
        if (!sig.signatureMetadata.proofChain.isValid()) {
            throw InvalidKeyException("Invalid signature key $sig. Key rotation proof chain is not valid.")
        }
    }
}

fun List<TransactionSignature>.resolveSigningKeysWithRotation(): Set<PublicKey> {
    return flatMap { signature ->
        // This is to cover the scenario where the required signing keys are still using the old key and have not yet rotated to the new key, but the signature is being provided with the new key.
        if(signature.signatureMetadata.proofChain != null && signature.signatureMetadata.proofChain.isNotEmpty()) {
            listOf(signature.signatureMetadata.proofChain.originalKey, signature.signatureMetadata.proofChain.currentKey)
        } else {
            listOf(signature.by)
        }
    }.toSet()
}

fun List<TransactionSignature>.getKeyLineageFromSignatures(): Set<PublicKey> {
    return flatMap { signature -> signature.signatureMetadata.proofChain.getKeyLineage() }.toSet()
}
