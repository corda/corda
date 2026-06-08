package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.TransactionSignature
import java.security.PublicKey

fun List<TransactionSignature>.getKeyLineage(): Set<PublicKey> {
    return mapNotNull { signature -> signature.signatureMetadata.proofChain?.getKeyLineage() }.flatten().toSet()
}

fun TransactionSignature.getOriginalKey(): PublicKey {
    if (signatureMetadata.proofChain != null && signatureMetadata.proofChain.isNotEmpty()) {
        // If there is a proof chain, the original key is the first key in the chain.
        return signatureMetadata.proofChain.originalKey
    }

    // No proof chain, so the original key is the same as the signing key.
    return by
}
