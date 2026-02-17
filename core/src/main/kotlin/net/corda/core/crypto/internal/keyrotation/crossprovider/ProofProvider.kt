package net.corda.core.crypto.internal.keyrotation.crossprovider

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.node.services.KeyManagementService
import java.security.PublicKey

internal interface ProofProvider {
    fun getProofChain(key: PublicKey): KeyRotationProofChain
}

internal class KmsProofProvider(private val kms: KeyManagementService): ProofProvider {
    override fun getProofChain(key: PublicKey) = kms.getProofChain(key)
}

internal class InMemoryProofProvider(private val map: Map<PublicKey, KeyRotationProofChain>): ProofProvider {
    override fun getProofChain(key: PublicKey) = map[key] ?: KeyRotationProofChain(emptyList())
}
