package net.corda.core.crypto

import java.security.KeyFactory
import java.security.KeyPairGeneratorSpi
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme.
 * @param schemeNumberID we assign a number ID for more efficient on-wire serialisation. Please ensure uniqueness between schemes.
 * @param schemeCodeName code name for this signature scheme (e.g. RSA_SHA256, ECDSA_SECP256K1_SHA256, ECDSA_SECP256R1_SHA256, EDDSA_ED25519_SHA512, SPHINCS-256_SHA512).
 * @param algorithmName which signature algorithm is used (e.g. RSA, ECDSA. EdDSA, SPHINCS-256).
 * @param sig the [Signature] class that provides the functionality of a digital signature scheme.
 * eg. Signature.getInstance("SHA256withECDSA", "BC").
 * @param keyFactory the KeyFactory for this scheme (e.g. KeyFactory.getInstance("RSA", "BC")).
 * @param keyPairGenerator defines the <i>Service Provider Interface</i> (<b>SPI</b>) for the {@code KeyPairGenerator} class.
 * e.g. KeyPairGenerator.getInstance("ECDSA", "BC").
 * @param algSpec parameter specs for the underlying algorithm. Note that RSA is defined by the key size rather than algSpec.
 * eg. ECGenParameterSpec("secp256k1").
 * @param keySize the private key size (currently used for RSA only).
 * @param desc a human-readable description for this scheme.
 */
data class SignatureScheme(
        val schemeNumberID: Int,
        val schemeCodeName: String,
        val algorithmName: String,
        val sig: Signature,
        val keyFactory: KeyFactory,
        val keyPairGenerator: KeyPairGeneratorSpi,
        val algSpec: AlgorithmParameterSpec?,
        val keySize: Int,
        val desc: String) {

    /**
     * KeyPair generators are always initialized once we create them, as no re-initialization is required.
     * Note that RSA is the sole algorithm initialized specifically by its supported keySize.
     */
    init {
        if (algSpec != null)
            keyPairGenerator.initialize(algSpec, newSecureRandom())
        else
            keyPairGenerator.initialize(keySize, newSecureRandom())
    }
}
