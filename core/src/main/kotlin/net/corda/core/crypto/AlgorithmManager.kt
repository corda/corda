package net.corda.core.crypto

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.spec.EdDSAGenParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import java.security.*
import java.security.spec.ECGenParameterSpec

/**
 * This object controls and provides the available and supported signature algorithms for Corda.
 * Any implemented [SignatureAlgorithm] should be defined here.
 * However, only the algorithms added in the supportedAlgorithm [HashMap] property will be fully supported,
 * while any other, even if implemented below, will be rejected with a [CryptoException] when requested for
 * any of the key generation, signing or verification processes.
 */
object SignatureAlgorithmManager {

    // Implemented signature algorithms. Note that we only support those added in the supportedAlgorithms map below.

    // RSA signature scheme using SHA256 as hash algorithm, 3072bit key, MGF1 (with SHA256) as mask generation function.
    private val RSA_SIGNATURE = SignatureAlgorithm (
            Signature.getInstance("SHA256WITHRSAANDMGF1", "BC"),
            KeyPairGenerator.getInstance("RSA", "BC"),
            null,
            3072,
            "RSA",
            "RSA signature scheme using SHA256 as hash algorithm, 3072bit key, MGF1 (with SHA256) as mask generation function."
    )

    // ECDSA signature scheme using the secp256k1 Koblitz curve.
    private val ECDSA_SIGNATURE = SignatureAlgorithm (
            Signature.getInstance("SHA256withECDSA", "BC"),
            KeyPairGenerator.getInstance("ECDSA", "BC"),
            ECGenParameterSpec("secp256k1"),
            256,
            "ECDSA",
            "ECDSA signature scheme using the secp256k1 Koblitz curve"
    )

    // EdDSA signature scheme using the ed255519 twisted Edwards curve.
    private val EDDSA_SIGNATURE = SignatureAlgorithm (
            EdDSAEngine(),
            net.i2p.crypto.eddsa.KeyPairGenerator(), // EdDSA engine uses a custom KeyPairGenerator Vs BouncyCastle.
            EdDSAGenParameterSpec(EdDSANamedCurveTable.CURVE_ED25519_SHA512),
            256,
            "EdDSA",
            "EdDSA signature scheme using the ed255519 twisted Edwards curve"

    )

    // SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers.
    private val SPHINCS_SIGNATURE = SignatureAlgorithm (
            Signature.getInstance("SHA512WITHSPHINCS256","BCPQC"),
            KeyPairGenerator.getInstance("SPHINCS256", "BCPQC"),
            SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
            256,
            "SPHINCS-256",
            "SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers."
    )

    // Supported signature algorithms
    private val supportedAlgorithms = hashMapOf(
            RSA_SIGNATURE.keyAlgorithm to RSA_SIGNATURE,
            ECDSA_SIGNATURE.keyAlgorithm to ECDSA_SIGNATURE,
            EDDSA_SIGNATURE.keyAlgorithm to EDDSA_SIGNATURE,
            SPHINCS_SIGNATURE.keyAlgorithm to SPHINCS_SIGNATURE
    )

    /**
     * Factory pattern to retrieve the corresponding [SignatureAlgorithm] based on the type of the [String] input.
     * This function is usually called by key generators.
     * In case the input is not a key in the supportedAlgorithms map, null will be returned.
     * @param algorithmName a [String] that should match a key in supportedAlgorithms map.
     * @return a SignatureAlgorithm of the ones currently supported or null.
     */
    fun findAlgorithm(algorithmName: String): SignatureAlgorithm? {
        return supportedAlgorithms.get(algorithmName)
    }

    /**
     * Retrieve the corresponding [SignatureAlgorithm] based on the type of the input [Key].
     * Note that it looks only on supported algorithms map.
     * This function is usually called when requiring to verify signatures.
     * @param key either private or public.
     * @return a SignatureAlgorithm of the ones currently supported or null.
     */
    fun findAlgorithm(key: Key): SignatureAlgorithm? {
        return supportedAlgorithms.values.firstOrNull { it.keyAlgorithm == key.algorithm }
    }

    /**
     * Retrieve the corresponding [SignatureAlgorithm] based on the type of the input [KeyPair].
     * Note that it looks only on supported algorithms map.
     * This function is usually called when requiring to sign signatures.
     * @param keyPair a [Keypair] of an assymetric encryption algorithm.
     * @return a SignatureAlgorithm of the ones currently supported or null.
     */
    fun findAlgorithm(keyPair: KeyPair): SignatureAlgorithm? {
        return findAlgorithm(keyPair.private)
    }

    /**
     * Get the list of supported signature algorithms.
     * @return a [List] of Strings with the codeNames for all of our supported algorithms.
     */
    fun listOfSupportedAlgorithms(): List<String> = supportedAlgorithms.keys.toList()

}
