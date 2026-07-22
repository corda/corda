package net.corda.core.crypto.internal.keyrotation.crossprovider

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.node.services.IdentityService
import java.security.PublicKey

internal interface ProofProvider {
    fun getProofChain(key: PublicKey): KeyRotationProofChain
}

internal class IdentityServiceProofProvider(private val identityService: IdentityService): ProofProvider {
    override fun getProofChain(key: PublicKey) = identityService.getProofChain(key)
}

internal class InMemoryProofProvider(private val map: Map<PublicKey, KeyRotationProofChain>): ProofProvider {
    override fun getProofChain(key: PublicKey) = map[key] ?: KeyRotationProofChain(emptyList())
}
