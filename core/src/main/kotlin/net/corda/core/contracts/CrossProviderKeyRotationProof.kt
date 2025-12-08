package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Instant

/**
 * A proof that a key rotation has taken place across different cryptographic providers.
 *
 * @param publicKeyOld The old public key before rotation.
 * @param publicKeyNew The new public key after rotation.
 * @param signature A signature created by the old private key over the new public key, proving the rotation.
 */
@CordaSerializable
data class CrossProviderKeyRotationProof(
        val publicKeyOld: PublicKey,
        val publicKeyNew: PublicKey,
        val signature: ByteArray
)
