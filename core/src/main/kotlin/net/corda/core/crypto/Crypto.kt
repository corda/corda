package net.corda.core.crypto

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * This object controls and provides the available and supported signature schemes for Corda.
 * Any implemented [SignatureScheme] should be strictly defined here.
 * However, only the schemes returned by {@link #listSupportedSignatureSchemes()} are supported.
 * Note that Corda currently supports the following signature schemes by their code names:
 * <p><ul>
 * <li>RSA_SHA256 (RSA using SHA256 as hash algorithm and MGF1 (with SHA256) as mask generation function).
 * <li>ECDSA_SECP256K1_SHA256 (ECDSA using the secp256k1 Koblitz curve and SHA256 as hash algorithm).
 * <li>ECDSA_SECP256R1_SHA256 (ECDSA using the secp256r1 (NIST P-256) curve and SHA256 as hash algorithm).
 * <li>EDDSA_ED25519_SHA512 (EdDSA using the ed255519 twisted Edwards curve and SHA512 as hash algorithm).
 * <li>SPHINCS256_SHA512 (SPHINCS-256 hash-based signature scheme using SHA512 as hash algorithm).
 * </ul>
 */
object Crypto {

    /**
     * RSA_SHA256 signature scheme using SHA256 as hash algorithm and MGF1 (with SHA256) as mask generation function.
     * Note: Recommended key size >= 3072 bits.
     */
    private val RSA_SHA256 = SignatureScheme(
            1,
            "RSA_SHA256",
            "RSA",
            Signature.getInstance("SHA256WITHRSAANDMGF1", "BC"),
            KeyFactory.getInstance("RSA", "BC"),
            KeyPairGenerator.getInstance("RSA", "BC"),
            null,
            3072,
            "RSA_SHA256 signature scheme using SHA256 as hash algorithm and MGF1 (with SHA256) as mask generation function."
    )

    /** ECDSA signature scheme using the secp256k1 Koblitz curve. */
    private val ECDSA_SECP256K1_SHA256 = SignatureScheme(
            2,
            "ECDSA_SECP256K1_SHA256",
            "ECDSA",
            Signature.getInstance("SHA256withECDSA", "BC"),
            KeyFactory.getInstance("ECDSA", "BC"),
            KeyPairGenerator.getInstance("ECDSA", "BC"),
            ECNamedCurveTable.getParameterSpec("secp256k1"),
            256,
            "ECDSA signature scheme using the secp256k1 Koblitz curve."
    )

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    private val ECDSA_SECP256R1_SHA256 = SignatureScheme(
            3,
            "ECDSA_SECP256R1_SHA256",
            "ECDSA",
            Signature.getInstance("SHA256withECDSA", "BC"),
            KeyFactory.getInstance("ECDSA", "BC"),
            KeyPairGenerator.getInstance("ECDSA", "BC"),
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            256,
            "ECDSA signature scheme using the secp256r1 (NIST P-256) curve."
    )

    /** EdDSA signature scheme using the ed255519 twisted Edwards curve. */
    private val EDDSA_ED25519_SHA512 = SignatureScheme(
            4,
            "EDDSA_ED25519_SHA512",
            "EdDSA",
            EdDSAEngine(),
            EdDSAKeyFactory,
            net.i2p.crypto.eddsa.KeyPairGenerator(), // EdDSA engine uses a custom KeyPairGenerator Vs BouncyCastle.
            EdDSANamedCurveTable.getByName("ed25519-sha-512"),
            256,
            "EdDSA signature scheme using the ed25519 twisted Edwards curve."
    )

    /**
     * SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers
     * at the cost of larger key sizes and loss of compatibility.
     */
    private val SPHINCS256_SHA256 = SignatureScheme(
            5,
            "SPHINCS-256_SHA512",
            "SPHINCS-256",
            Signature.getInstance("SHA512WITHSPHINCS256", "BCPQC"),
            KeyFactory.getInstance("SPHINCS256", "BCPQC"),
            KeyPairGenerator.getInstance("SPHINCS256", "BCPQC"),
            SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
            256,
            "SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers " +
                    "at the cost of larger key sizes and loss of compatibility."
    )

    /** Our default signature scheme if no algorithm is specified (e.g. for key generation). */
    private val DEFAULT_SIGNATURE_SCHEME = EDDSA_ED25519_SHA512

    /**
     * Supported digital signature schemes.
     * Note: Only the schemes added in this map will be supported (see [Crypto]).
     * Do not forget to add the DEFAULT_SIGNATURE_SCHEME as well.
     */
    private val supportedSignatureSchemes = mapOf(
            RSA_SHA256.schemeCodeName to RSA_SHA256,
            ECDSA_SECP256K1_SHA256.schemeCodeName to ECDSA_SECP256K1_SHA256,
            ECDSA_SECP256R1_SHA256.schemeCodeName to ECDSA_SECP256R1_SHA256,
            EDDSA_ED25519_SHA512.schemeCodeName to EDDSA_ED25519_SHA512,
            SPHINCS256_SHA256.schemeCodeName to SPHINCS256_SHA256
    )

    /**
     * Factory pattern to retrieve the corresponding [SignatureScheme] based on the type of the [String] input.
     * This function is usually called by key generators and verify signature functions.
     * In case the input is not a key in the supportedSignatureSchemes map, null will be returned.
     * @param schemeCodeName a [String] that should match a supported signature scheme code name (e.g. ECDSA_SECP256K1_SHA256), see [Crypto].
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    private fun findSignatureScheme(schemeCodeName: String): SignatureScheme = supportedSignatureSchemes[schemeCodeName] ?: throw IllegalArgumentException("Unsupported key/algorithm for metadata schemeCodeName: $schemeCodeName")

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [KeyPair].
     * Note that only the Corda platform standard schemes are supported (see [Crypto]).
     * This function is usually called when requiring to sign signatures.
     * @param keyPair a cryptographic [KeyPair].
     * @return a currently supported SignatureScheme or null.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    private fun findSignatureScheme(keyPair: KeyPair): SignatureScheme = findSignatureScheme(keyPair.private)

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [Key].
     * This function is usually called when requiring to verify signatures and the signing schemes must be defined.
     * Note that only the Corda platform standard schemes are supported (see [Crypto]).
     * Note that we always need to add an additional if-else statement when there are signature schemes
     * with the same algorithmName, but with different parameters (e.g. now there are two ECDSA schemes, each using its own curve).
     * @param key either private or public.
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    private fun findSignatureScheme(key: Key): SignatureScheme {
        for (sig in supportedSignatureSchemes.values) {
            val algorithm = key.algorithm
            if (algorithm == sig.algorithmName) {
                // If more than one ECDSA schemes are supported, we should distinguish between them by checking their curve parameters.
                // TODO: change 'continue' to 'break' if only one EdDSA curve will be used.
                if (algorithm == "EdDSA") {
                    if ((key as EdDSAKey).params == sig.algSpec) {
                        return sig
                    } else continue
                } else if (algorithm == "ECDSA") {
                    if ((key as ECKey).parameters == sig.algSpec) {
                        return sig
                    } else continue
                } else return sig // it's either RSA_SHA256 or SPHINCS-256.
            }
        }
        throw IllegalArgumentException("Unsupported key/algorithm for the private key: ${key.encoded.toBase58()}")
    }

    /**
     * Retrieve the corresponding signature scheme code name based on the type of the input [Key].
     * See [Crypto] for the supported scheme code names.
     * @param key either private or public.
     * @return signatureSchemeCodeName for a [Key].
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findSignatureSchemeCodeName(key: Key): String = findSignatureScheme(key).schemeCodeName

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object.
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class)
    fun decodePrivateKey(encodedKey: ByteArray): PrivateKey {
        for ((_, _, _, _, keyFactory) in supportedSignatureSchemes.values) {
            try {
                return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
            } catch (ikse: InvalidKeySpecException) {
                // ignore it - only used to bypass the scheme that causes an exception.
            }
        }
        throw IllegalArgumentException("This private key cannot be decoded, please ensure it is PKCS8 encoded and the signature scheme is supported.")
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This will be used by Kryo deserialisation.
     * @param encodedKey a PKCS8 encoded private key.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePrivateKey(encodedKey: ByteArray, schemeCodeName: String): PrivateKey {
        val sig = findSignatureScheme(schemeCodeName)
        try {
            return sig.keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw InvalidKeySpecException("This private key cannot be decoded, please ensure it is PKCS8 encoded and that it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Decode an X509 encoded key to its [PublicKey] object.
     * @param encodedKey an X509 encoded public key.
     * @throws UnsupportedSchemeException on not supported scheme.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class)
    fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        for ((_, _, _, _, keyFactory) in supportedSignatureSchemes.values) {
            try {
                return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
            } catch (ikse: InvalidKeySpecException) {
                // ignore it - only used to bypass the scheme that causes an exception.
            }
        }
        throw IllegalArgumentException("This public key cannot be decoded, please ensure it is X509 encoded and the signature scheme is supported.")
    }

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This will be used by Kryo deserialisation.
     * @param encodedKey an X509 encoded public key.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @throws IllegalArgumentException if the requested scheme is not supported
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePublicKey(encodedKey: ByteArray, schemeCodeName: String): PublicKey {
        val sig = findSignatureScheme(schemeCodeName)
        try {
            return sig.keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw throw InvalidKeySpecException("This public key cannot be decoded, please ensure it is X509 encoded and that it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Utility to simplify the act of generating keys.
     * Normally, we don't expect other errors here, assuming that key generation parameters for every supported signature scheme have been unit-tested.
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
     * @return a KeyPair for the requested scheme.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @Throws(IllegalArgumentException::class)
    fun generateKeyPair(schemeCodeName: String): KeyPair = findSignatureScheme(schemeCodeName).keyPairGenerator.generateKeyPair()

    /**
     * Generate a KeyPair using the default signature scheme.
     * @return a new KeyPair.
     */
    fun generateKeyPair(): KeyPair = DEFAULT_SIGNATURE_SCHEME.keyPairGenerator.generateKeyPair()

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey]. Strategy on on identifying the actual signing scheme is based
     * on the [PrivateKey] type, but if the schemeCodeName is known, then better use doSign(signatureScheme: String, privateKey: PrivateKey, clearData: ByteArray).
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
    fun doSign(privateKey: PrivateKey, clearData: ByteArray) = doSign(findSignatureScheme(privateKey).sig, privateKey, clearData)

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey] and a known schemeCodeName [String].
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
    fun doSign(schemeCodeName: String, privateKey: PrivateKey, clearData: ByteArray) = doSign(findSignatureScheme(schemeCodeName).sig, privateKey, clearData)

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey] and a known [Signature].
     * @param signature a [Signature] object, retrieved from supported signature schemes, see [Crypto].
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
    private fun doSign(signature: Signature, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        if (clearData.isEmpty()) throw Exception("Signing of an empty array is not permitted!")
        signature.initSign(privateKey)
        signature.update(clearData)
        return signature.sign()
    }

    /**
     * Generic way to sign [MetaData] objects with a [PrivateKey].
     * [MetaData] is a wrapper over the transaction's Merkle root in order to attach extra information, such as a timestamp or partial and blind signature indicators.
     * @param privateKey the signer's [PrivateKey].
     * @param metaData a [MetaData] object that adds extra information to a transaction.
     * @return a [TransactionSignature] object than contains the output of a successful signing and the metaData.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or
     * if metaData.schemeCodeName is not aligned with key type.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
    fun doSign(privateKey: PrivateKey, metaData: MetaData): TransactionSignature {
        val sigKey: SignatureScheme = findSignatureScheme(privateKey)
        val sigMetaData: SignatureScheme = findSignatureScheme(metaData.schemeCodeName)
        if (sigKey != sigMetaData) throw IllegalArgumentException("Metadata schemeCodeName: ${metaData.schemeCodeName} is not aligned with the key type.")
        val signatureData = doSign(sigKey.schemeCodeName, privateKey, metaData.bytes())
        return TransactionSignature(signatureData, metaData)
    }

    /**
     * Utility to simplify the act of verifying a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) = doVerify(findSignatureScheme(schemeCodeName).sig, publicKey, signatureData, clearData)

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the input public key's type.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * Strategy on identifying the actual signing scheme is based on the [PublicKey] type, but if the schemeCodeName is known,
     * then better use doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray).
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun doVerify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) = doVerify(findSignatureScheme(publicKey).sig, publicKey, signatureData, clearData)

    /**
     * Method to verify a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param signature a [Signature] object, retrieved from supported signature schemes, see [Crypto].
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if any of the clear or signature data is empty.
     */
    private fun doVerify(signature: Signature, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        if (signatureData.isEmpty()) throw IllegalArgumentException("Signature data is empty!")
        if (clearData.isEmpty()) throw IllegalArgumentException("Clear data is empty, nothing to verify!")
        signature.initVerify(publicKey)
        signature.update(clearData)
        val verificationResult = signature.verify(signatureData)
        if (verificationResult) {
            return true
        } else {
            throw SignatureException("Signature Verification failed!")
        }
    }

    /**
     * Utility to simplify the act of verifying a [TransactionSignature].
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param publicKey the signer's [PublicKey].
     * @param transactionSignature the signatureData on a message.
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun doVerify(publicKey: PublicKey, transactionSignature: TransactionSignature): Boolean {
        if (publicKey != transactionSignature.metaData.publicKey) IllegalArgumentException("MetaData's publicKey: ${transactionSignature.metaData.publicKey.encoded.toBase58()} does not match the input clearData: ${publicKey.encoded.toBase58()}")
        return Crypto.doVerify(publicKey, transactionSignature.signatureData, transactionSignature.metaData.bytes())
    }

    /**
     * Check if the requested signature scheme is supported by the system.
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
     * @return true if the signature scheme is supported.
     */
    fun isSupportedSignatureScheme(schemeCodeName: String): Boolean = schemeCodeName in supportedSignatureSchemes

    /** @return the default signature scheme's code name. */
    fun getDefaultSignatureSchemeCodeName(): String = DEFAULT_SIGNATURE_SCHEME.schemeCodeName

    /** @return a [List] of Strings with the scheme code names defined in [SignatureScheme] for all of our supported signature schemes, see [Crypto]. */
    fun listSupportedSignatureSchemes(): List<String> = supportedSignatureSchemes.keys.toList()
}
