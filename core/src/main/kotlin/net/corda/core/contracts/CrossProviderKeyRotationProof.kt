package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Instant

@CordaSerializable
data class CrossProviderKeyRotationProof(
        val publicKeyOld: PublicKey,
        val publicKeyNew: PublicKey,
        val signature: ByteArray,
        val timestamp: Instant,
        val issuer: String // Change this name to signerName
)
