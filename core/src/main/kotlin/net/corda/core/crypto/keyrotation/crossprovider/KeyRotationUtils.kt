package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.TransactionSignature
import java.security.InvalidKeyException
import java.security.PublicKey
import kotlin.collections.forEach

fun List<TransactionSignature>.ensureKeyRotationProofChainValid() {
        forEach { sig ->
            if(!sig.signatureMetadata.proofChain.isValid()) {
                throw InvalidKeyException("Invalid signature key $sig. Key rotation proof chain is not valid.")
            }
        }
    }

fun List<TransactionSignature>.getKeyLineageFromSignatures(): Set<PublicKey> {
    return flatMap { signature -> signature.signatureMetadata.proofChain.getKeyLineage() }.toSet()
}
