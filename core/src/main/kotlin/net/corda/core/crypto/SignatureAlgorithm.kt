package net.corda.core.crypto

import net.corda.core.crypto.Crypto.safeRandom
import java.security.*
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a signature algorithm.
 * @param sig the [Signature] class that provides the functionality of a digital signature algorithm.
 * eg. Signature.getInstance("SHA256withECDSA", "BC").
 * @param keyPairGenerator defines the <i>Service Provider Interface</i> (<b>SPI</b>) for the {@code KeyPairGenerator} class.
 * eg. KeyPairGenerator.getInstance("ECDSA", "BC").
 * @param algSpec parameter specs for the underlying algorithm. Note that RSA is defined by the key size rather than algSpec.
 * eg. ECGenParameterSpec("secp256k1").
 * @param keySize the private key size (currently used for RSA only).
 * @param desc a human-readable description for this algorithm.
 */
data class SignatureAlgorithm (
        val sig: Signature,
        val keyPairGenerator: KeyPairGeneratorSpi,
        val algSpec: AlgorithmParameterSpec?,
        val keySize: Int,
        val keyAlgorithm: String,
        val desc: String) {

    /**
     * KeyPair generators are always initialized once we create them, as no re-initialization is required.
     * Note that RSA is the sole algorithm initialized specifically by it's supported keySize.
     */
    init {
        if (algSpec != null)
            keyPairGenerator.initialize(algSpec, safeRandom())
        else
            keyPairGenerator.initialize(keySize, safeRandom())
    }
}

