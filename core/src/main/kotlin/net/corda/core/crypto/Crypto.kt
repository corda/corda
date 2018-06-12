/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.internal.*
import net.corda.core.serialization.serialize
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.math.GroupElement
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECConstants
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.math.ec.WNafUtil
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import java.math.BigInteger
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * This object controls and provides the available and supported signature schemes for Corda.
 * Any implemented [SignatureScheme] should be strictly defined here.
 * However, only the schemes returned by {@link #listSupportedSignatureSchemes()} are supported.
 * Note that Corda currently supports the following signature schemes by their code names:
 * <p><ul>
 * <li>RSA_SHA256 (RSA PKCS#1 using SHA256 as hash algorithm).
 * <li>ECDSA_SECP256K1_SHA256 (ECDSA using the secp256k1 Koblitz curve and SHA256 as hash algorithm).
 * <li>ECDSA_SECP256R1_SHA256 (ECDSA using the secp256r1 (NIST P-256) curve and SHA256 as hash algorithm).
 * <li>EDDSA_ED25519_SHA512 (EdDSA using the ed255519 twisted Edwards curve and SHA512 as hash algorithm).
 * <li>SPHINCS256_SHA512 (SPHINCS-256 hash-based signature scheme using SHA512 as hash algorithm).
 * </ul>
 */
@KeepForDJVM
object Crypto {
    /**
     * RSA PKCS#1 signature scheme using SHA256 for message hashing.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @JvmField
    val RSA_SHA256 = SignatureScheme(
            1,
            "RSA_SHA256",
            AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, null),
            listOf(AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, null)),
            cordaBouncyCastleProvider.name,
            "RSA",
            "SHA256WITHRSA",
            null,
            3072,
            "RSA_SHA256 signature scheme using SHA256 as hash algorithm."
    )

    /** ECDSA signature scheme using the secp256k1 Koblitz curve and SHA256 for message hashing. */
    @JvmField
    val ECDSA_SECP256K1_SHA256 = SignatureScheme(
            2,
            "ECDSA_SECP256K1_SHA256",
            AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1),
            listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
            cordaBouncyCastleProvider.name,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256k1"),
            256,
            "ECDSA signature scheme using the secp256k1 Koblitz curve."
    )

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve and SHA256 for message hashing. */
    @JvmField
    val ECDSA_SECP256R1_SHA256 = SignatureScheme(
            3,
            "ECDSA_SECP256R1_SHA256",
            AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256r1),
            listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256r1)),
            cordaBouncyCastleProvider.name,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            256,
            "ECDSA signature scheme using the secp256r1 (NIST P-256) curve."
    )

    /**
     * EdDSA signature scheme using the ed25519 twisted Edwards curve and SHA512 for message hashing.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @JvmField
    val EDDSA_ED25519_SHA512: SignatureScheme = SignatureScheme(
            4,
            "EDDSA_ED25519_SHA512",
            AlgorithmIdentifier(`id-Curve25519ph`, null),
            emptyList(), // Both keys and the signature scheme use the same OID in i2p library.
            // We added EdDSA to bouncy castle for certificate signing.
            cordaBouncyCastleProvider.name,
            "1.3.101.112",
            EdDSAEngine.SIGNATURE_ALGORITHM,
            EdDSANamedCurveTable.getByName("ED25519"),
            256,
            "EdDSA signature scheme using the ed25519 twisted Edwards curve."
    )

    /** DLSequence (ASN1Sequence) for SHA512 truncated to 256 bits, used in SPHINCS-256 signature scheme. */
    @JvmField
    val SHA512_256 = DLSequence(arrayOf(NISTObjectIdentifiers.id_sha512_256))

    /**
     * SPHINCS-256 hash-based signature scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
    // TODO: change val name to SPHINCS256_SHA512. This will break backwards compatibility.
    @JvmField
    val SPHINCS256_SHA256 = SignatureScheme(
            5,
            "SPHINCS-256_SHA512",
            AlgorithmIdentifier(BCObjectIdentifiers.sphincs256_with_SHA512, DLSequence(arrayOf(ASN1Integer(0), SHA512_256))),
            listOf(AlgorithmIdentifier(BCObjectIdentifiers.sphincs256, DLSequence(arrayOf(ASN1Integer(0), SHA512_256)))),
            bouncyCastlePQCProvider.name,
            "SPHINCS256",
            "SHA512WITHSPHINCS256",
            SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
            256,
            "SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers " +
                    "at the cost of larger key sizes and loss of compatibility."
    )

    /** Corda [CompositeKey] signature type. */
    // TODO: change the val name to a more descriptive one as it's now confusing and looks like a Key type.
    @JvmField
    val COMPOSITE_KEY = SignatureScheme(
            6,
            "COMPOSITE",
            AlgorithmIdentifier(CordaObjectIdentifier.COMPOSITE_KEY),
            emptyList(),
            cordaSecurityProvider.name,
            CompositeKey.KEY_ALGORITHM,
            CompositeSignature.SIGNATURE_ALGORITHM,
            null,
            null,
            "Composite keys composed from individual public keys"
    )

    /** Our default signature scheme if no algorithm is specified (e.g. for key generation). */
    @JvmField
    val DEFAULT_SIGNATURE_SCHEME = EDDSA_ED25519_SHA512

    /**
     * Supported digital signature schemes.
     * Note: Only the schemes added in this map will be supported (see [Crypto]).
     */
    private val signatureSchemeMap: Map<String, SignatureScheme> = listOf(
            RSA_SHA256,
            ECDSA_SECP256K1_SHA256,
            ECDSA_SECP256R1_SHA256,
            EDDSA_ED25519_SHA512,
            SPHINCS256_SHA256,
            COMPOSITE_KEY
    ).associateBy { it.schemeCodeName }

    /**
     * Map of X.509 algorithm identifiers to signature schemes Corda recognises. See RFC 2459 for the format of
     * algorithm identifiers.
     */
    private val algorithmMap: Map<AlgorithmIdentifier, SignatureScheme>
            = (signatureSchemeMap.values.flatMap { scheme -> scheme.alternativeOIDs.map { Pair(it, scheme) } }
            + signatureSchemeMap.values.map { Pair(it.signatureOID, it) })
            .toMap()

    @JvmStatic
    fun supportedSignatureSchemes(): List<SignatureScheme> = ArrayList(signatureSchemeMap.values)

    @JvmStatic
    fun findProvider(name: String): Provider {
        return providerMap[name] ?: throw IllegalArgumentException("Unrecognised provider: $name")
    }

    /**
     * Normalise an algorithm identifier by converting [DERNull] parameters into a Kotlin null value.
     */
    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier {
        return if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
    }

    @JvmStatic
    fun findSignatureScheme(algorithm: AlgorithmIdentifier): SignatureScheme {
        return algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
                ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}")
    }

    /**
     * Factory pattern to retrieve the corresponding [SignatureScheme] based on [SignatureScheme.schemeCodeName].
     * This function is usually called by key generators and verify signature functions.
     * In case the input is not a key in the supportedSignatureSchemes map, null will be returned.
     * @param schemeCodeName a [String] that should match a supported signature scheme code name (e.g. ECDSA_SECP256K1_SHA256), see [Crypto].
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @JvmStatic
    fun findSignatureScheme(schemeCodeName: String): SignatureScheme {
        return signatureSchemeMap[schemeCodeName]
                ?: throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $schemeCodeName")
    }

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [Key].
     * This function is usually called when requiring to verify signatures and the signing schemes must be defined.
     * For the supported signature schemes see [Crypto].
     * @param key either private or public.
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    @JvmStatic
    fun findSignatureScheme(key: PublicKey): SignatureScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findSignatureScheme(keyInfo.algorithm)
    }

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [Key].
     * This function is usually called when requiring to verify signatures and the signing schemes must be defined.
     * For the supported signature schemes see [Crypto].
     * @param key either private or public.
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    @JvmStatic
    fun findSignatureScheme(key: PrivateKey): SignatureScheme {
        val keyInfo = PrivateKeyInfo.getInstance(key.encoded)
        return findSignatureScheme(keyInfo.privateKeyAlgorithm)
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object.
     * Use this method if the key type is a-priori unknown.
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    fun decodePrivateKey(encodedKey: ByteArray): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(encodedKey)
        val signatureScheme = findSignatureScheme(keyInfo.privateKeyAlgorithm)
        val keyFactory = KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePrivateKey(schemeCodeName: String, encodedKey: ByteArray): PrivateKey {
        return decodePrivateKey(findSignatureScheme(schemeCodeName), encodedKey)
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePrivateKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PrivateKey {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        try {
            val keyFactory = KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
            return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw InvalidKeySpecException("This private key cannot be decoded, please ensure it is PKCS8 encoded and that " +
                    "it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Decode an X509 encoded key to its [PublicKey] object.
     * Use this method if the key type is a-priori unknown.
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
        val signatureScheme = findSignatureScheme(subjectPublicKeyInfo.algorithm)
        val keyFactory = KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException if the requested scheme is not supported.
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePublicKey(schemeCodeName: String, encodedKey: ByteArray): PublicKey {
        return decodePublicKey(findSignatureScheme(schemeCodeName), encodedKey)
    }

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException if the requested scheme is not supported.
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePublicKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PublicKey {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        try {
            val keyFactory = KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
            return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw throw InvalidKeySpecException("This public key cannot be decoded, please ensure it is X509 encoded and " +
                    "that it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey]. Strategy on on identifying the actual signing scheme is based
     * on the [PrivateKey] type, but if the schemeCodeName is known, then better use
     * doSign(signatureScheme: String, privateKey: PrivateKey, clearData: ByteArray).
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(privateKey: PrivateKey, clearData: ByteArray): ByteArray = doSign(findSignatureScheme(privateKey), privateKey, clearData)

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
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(schemeCodeName: String, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        return doSign(findSignatureScheme(schemeCodeName), privateKey, clearData)
    }

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey] and a known [Signature].
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(signatureScheme: SignatureScheme, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        require(clearData.isNotEmpty()) { "Signing of an empty array is not permitted!" }
        val signature = Signature.getInstance(signatureScheme.signatureName, providerMap[signatureScheme.providerName])
        signature.initSign(privateKey)
        signature.update(clearData)
        return signature.sign()
    }

    /**
     * Generic way to sign [SignableData] objects with a [PrivateKey].
     * [SignableData] is a wrapper over the transaction's id (Merkle root) in order to attach extra information, such as
     * a timestamp or partial and blind signature indicators.
     * @param keyPair the signer's [KeyPair].
     * @param signableData a [SignableData] object that adds extra information to a transaction.
     * @return a [TransactionSignature] object than contains the output of a successful signing, signer's public key and
     * the signature metadata.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(keyPair: KeyPair, signableData: SignableData): TransactionSignature {
        val sigKey: SignatureScheme = findSignatureScheme(keyPair.private)
        val sigMetaData: SignatureScheme = findSignatureScheme(keyPair.public)
        require(sigKey == sigMetaData) {
            "Metadata schemeCodeName: ${sigMetaData.schemeCodeName} is not aligned with the key type: ${sigKey.schemeCodeName}."
        }
        val signatureBytes = doSign(sigKey.schemeCodeName, keyPair.private, signableData.serialize().bytes)
        return TransactionSignature(signatureBytes, keyPair.public, signableData.signatureMetadata)
    }

    /**
     * Utility to simplify the act of verifying a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
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
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return doVerify(findSignatureScheme(schemeCodeName), publicKey, signatureData, clearData)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the input public key's type.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * Strategy on identifying the actual signing scheme is based on the [PublicKey] type, but if the schemeCodeName is known,
     * then better use doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray).
     *
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
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return doVerify(findSignatureScheme(publicKey), publicKey, signatureData, clearData)
    }

    /**
     * Method to verify a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
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
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        if (signatureData.isEmpty()) throw IllegalArgumentException("Signature data is empty!")
        if (clearData.isEmpty()) throw IllegalArgumentException("Clear data is empty, nothing to verify!")
        val verificationResult = isValid(signatureScheme, publicKey, signatureData, clearData)
        if (verificationResult) {
            return true
        } else {
            throw SignatureException("Signature Verification failed!")
        }
    }

    /**
     * Utility to simplify the act of verifying a [TransactionSignature].
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param txId transaction's id.
     * @param transactionSignature the signature on the transaction.
     * @return true if verification passes or throw exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        val signableData = SignableData(originalSignedHash(txId, transactionSignature.partialMerkleTree), transactionSignature.signatureMetadata)
        return Crypto.doVerify(transactionSignature.by, transactionSignature.bytes, signableData.serialize().bytes)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the
     * input public key's type.
     * It returns true if it succeeds and false if not. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * @param txId transaction's id.
     * @param transactionSignature the signature on the transaction.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        val signableData = SignableData(originalSignedHash(txId, transactionSignature.partialMerkleTree), transactionSignature.signatureMetadata)
        return isValid(
                findSignatureScheme(transactionSignature.by),
                transactionSignature.by,
                transactionSignature.bytes,
                signableData.serialize().bytes)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the
     * input public key's type.
     * It returns true if it succeeds and false if not. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * Use this method if the signature scheme is not a-priori known.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return isValid(findSignatureScheme(publicKey), publicKey, signatureData, clearData)
    }

    /**
     * Method to verify a digital signature. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception.
     * Use this method if the signature scheme type is a-priori unknown.
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        val signature = Signature.getInstance(signatureScheme.signatureName, providerMap[signatureScheme.providerName])
        signature.initVerify(publicKey)
        signature.update(clearData)
        return signature.verify(signatureData)
    }

    /**
     * Utility to simplify the act of generating keys.
     * Normally, we don't expect other errors here, assuming that key generation parameters for every supported signature scheme have been unit-tested.
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
     * @return a KeyPair for the requested signature scheme code name.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @DeleteForDJVM
    @JvmStatic
    fun generateKeyPair(schemeCodeName: String): KeyPair = generateKeyPair(findSignatureScheme(schemeCodeName))

    /**
     * Generate a [KeyPair] for the selected [SignatureScheme].
     * Note that RSA is the sole algorithm initialized specifically by its supported keySize.
     * @param signatureScheme a supported [SignatureScheme], see [Crypto], default to [DEFAULT_SIGNATURE_SCHEME] if not provided.
     * @return a new [KeyPair] for the requested [SignatureScheme].
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @DeleteForDJVM
    @JvmOverloads
    @JvmStatic
    fun generateKeyPair(signatureScheme: SignatureScheme = DEFAULT_SIGNATURE_SCHEME): KeyPair {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
        if (signatureScheme.algSpec != null)
            keyPairGenerator.initialize(signatureScheme.algSpec, newSecureRandom())
        else
            keyPairGenerator.initialize(signatureScheme.keySize!!, newSecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Deterministically generate/derive a [KeyPair] using an existing private key and a seed as inputs.
     * This operation is currently supported for ECDSA secp256r1 (NIST P-256), ECDSA secp256k1 and EdDSA ed25519.
     *
     * Similarly to BIP32, the implemented algorithm uses an HMAC function based on SHA512 and it is actually
     * an implementation the HKDF rfc - Step 1: Extract function,
     * @see <a href="https://tools.ietf.org/html/rfc5869">HKDF</a>
     * which is practically a variation of the private-parent-key -> private-child-key hardened key generation of BIP32.
     *
     * Unlike BIP32, where both private and public keys are extended to prevent deterministically
     * generated child keys from depending solely on the key itself, current method uses normal elliptic curve keys
     * without a chain-code and the generated key relies solely on the security of the private key.
     *
     * Although without a chain-code we lose the aforementioned property of not depending solely on the key,
     * it should be mentioned that the cryptographic strength of the HMAC depends upon the size of the secret key.
     * @see <a href="https://en.wikipedia.org/wiki/Hash-based_message_authentication_code#Security">HMAC Security</a>
     * Thus, as long as the master key is kept secret and has enough entropy (~256 bits for EC-schemes), the system
     * is considered secure.
     *
     * It is also a fact that if HMAC is used as PRF and/or MAC but not as checksum function, the function is still
     * secure even if the underlying hash function is not collision resistant (e.g. if we used MD5).
     * In practice, for our DKG purposes (thus PRF), a collision would not necessarily reveal the master HMAC key,
     * because multiple inputs can produce the same hash output.
     *
     * Also according to the HMAC-based Extract-and-Expand Key Derivation Function (HKDF) rfc5869:
     * <p><ul>
     * <li>a chain-code (aka the salt) is recommended, but not required.
     * <li>the salt can be public, but a hidden one provides stronger security guarantee.
     * <li>even a simple counter can work as a salt, but ideally it should be random.
     * <li>salt values should not be chosen by an attacker.
     * </ul></p>
     *
     * Regarding the last requirement, according to Krawczyk's HKDF scheme: <i>While there is no need to keep the salt secret,
     * it is assumed that salt values are independent of the input keying material</i>.
     * @see <a href="http://eprint.iacr.org/2010/264.pdf">Cryptographic Extraction and Key Derivation - The HKDF Scheme</a>.
     *
     * There are also protocols that require an authenticated nonce (e.g. when a DH derived key is used as a seed) and thus
     * we need to make sure that nonces come from legitimate parties rather than selected by an attacker.
     * Similarly, in DLT systems, proper handling is required if users should agree on a common value as a seed,
     * e.g. a transaction's nonce or hash.
     *
     * Moreover if a unique key per transaction is prerequisite, an attacker should never force a party to reuse a
     * previously used key, due to privacy and forward secrecy reasons.
     *
     * All in all, this algorithm can be used with a counter as seed, however it is suggested that the output does
     * not solely depend on the key, i.e. a secret salt per user or a random nonce per transaction could serve this role.
     * In case where a non-random seed policy is selected, such as the BIP32 counter logic, one needs to carefully keep state
     * so that the same salt is used only once.
     *
     * @param signatureScheme the [SignatureScheme] of the private key input.
     * @param privateKey the [PrivateKey] that will be used as key to the HMAC-ed DKG function.
     * @param seed an extra seed that will be used as value to the underlying HMAC.
     * @return a new deterministically generated [KeyPair].
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     * @throws UnsupportedOperationException if deterministic key generation is not supported for this particular scheme.
     */
    @JvmStatic
    fun deriveKeyPair(signatureScheme: SignatureScheme, privateKey: PrivateKey, seed: ByteArray): KeyPair {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        return when (signatureScheme) {
            ECDSA_SECP256R1_SHA256, ECDSA_SECP256K1_SHA256 -> deriveKeyPairECDSA(signatureScheme.algSpec as ECParameterSpec, privateKey, seed)
            EDDSA_ED25519_SHA512 -> deriveKeyPairEdDSA(privateKey, seed)
            else -> throw UnsupportedOperationException("Although supported for signing, deterministic key derivation is " +
                    "not currently implemented for ${signatureScheme.schemeCodeName}")
        }
    }

    /**
     * Deterministically generate/derive a [KeyPair] using an existing private key and a seed as inputs.
     * Use this method if the [SignatureScheme] of the private key input is not known.
     * @param privateKey the [PrivateKey] that will be used as key to the HMAC-ed DKG function.
     * @param seed an extra seed that will be used as value to the underlying HMAC.
     * @return a new deterministically generated [KeyPair].
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     * @throws UnsupportedOperationException if deterministic key generation is not supported for this particular scheme.
     */
    @JvmStatic
    fun deriveKeyPair(privateKey: PrivateKey, seed: ByteArray): KeyPair {
        return deriveKeyPair(findSignatureScheme(privateKey), privateKey, seed)
    }

    // Given the domain parameters, this routine deterministically generates an ECDSA key pair
    // in accordance with X9.62 section 5.2.1 pages 26, 27.
    private fun deriveKeyPairECDSA(parameterSpec: ECParameterSpec, privateKey: PrivateKey, seed: ByteArray): KeyPair {
        // Compute HMAC(privateKey, seed).
        val macBytes = deriveHMAC(privateKey, seed)
        // Get the first EC curve fieldSized-bytes from macBytes.
        // According to recommendations from the deterministic ECDSA rfc, see https://tools.ietf.org/html/rfc6979
        // performing a simple modular reduction would induce biases that would be detrimental to security.
        // Thus, the result is not reduced modulo q and similarly to BIP32, EC curve fieldSized-bytes are utilised.
        val fieldSizeMacBytes = macBytes.copyOf(parameterSpec.curve.fieldSize / 8)

        // Calculate value d for private key.
        val deterministicD = BigInteger(1, fieldSizeMacBytes)

        // Key generation checks follow the BC logic found in
        // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/crypto/generators/ECKeyPairGenerator.java
        // There is also an extra check to align with the BIP32 protocol, according to which
        // if deterministicD >= order_of_the_curve the resulted key is invalid and we should proceed with another seed.
        // TODO: We currently use SHA256(seed) when retrying, but BIP32 just skips a counter (i) that results to an invalid key.
        //       Although our hashing approach seems reasonable, we should check if there are alternatives,
        //       especially if we use counters as well.
        if (deterministicD < ECConstants.TWO
                || WNafUtil.getNafWeight(deterministicD) < parameterSpec.n.bitLength().ushr(2)
                || deterministicD >= parameterSpec.n) {
            // Instead of throwing an exception, we retry with SHA256(seed).
            return deriveKeyPairECDSA(parameterSpec, privateKey, seed.sha256().bytes)
        }
        val privateKeySpec = ECPrivateKeySpec(deterministicD, parameterSpec)
        val privateKeyD = BCECPrivateKey(privateKey.algorithm, privateKeySpec, BouncyCastleProvider.CONFIGURATION)

        // Compute the public key by scalar multiplication.
        // Note that BIP32 uses masterKey + mac_derived_key as the final private key and it consequently
        // requires an extra point addition: master_public + mac_derived_public for the public part.
        // In our model, the mac_derived_output, deterministicD, is not currently added to the masterKey and it
        // it forms, by itself, the new private key, which in turn is used to compute the new public key.
        val pointQ = FixedPointCombMultiplier().multiply(parameterSpec.g, deterministicD)
        // This is unlikely to happen, but we should check for point at infinity.
        if (pointQ.isInfinity) {
            // Instead of throwing an exception, we retry with SHA256(seed).
            return deriveKeyPairECDSA(parameterSpec, privateKey, seed.sha256().bytes)
        }
        val publicKeySpec = ECPublicKeySpec(pointQ, parameterSpec)
        val publicKeyD = BCECPublicKey(privateKey.algorithm, publicKeySpec, BouncyCastleProvider.CONFIGURATION)

        return KeyPair(publicKeyD, privateKeyD)
    }

    // Deterministically generate an EdDSA key.
    private fun deriveKeyPairEdDSA(privateKey: PrivateKey, seed: ByteArray): KeyPair {
        // Compute HMAC(privateKey, seed).
        val macBytes = deriveHMAC(privateKey, seed)

        // Calculate key pair.
        val params = EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec
        val bytes = macBytes.copyOf(params.curve.field.getb() / 8) // Need to pad the entropy to the valid seed length.
        val privateKeyD = EdDSAPrivateKeySpec(bytes, params)
        val publicKeyD = EdDSAPublicKeySpec(privateKeyD.a, params)
        return KeyPair(EdDSAPublicKey(publicKeyD), EdDSAPrivateKey(privateKeyD))
    }

    /**
     * Returns a key pair derived from the given [BigInteger] entropy. This is useful for unit tests
     * and other cases where you want hard-coded private keys.
     * Currently, the following schemes are supported: [EDDSA_ED25519_SHA512], [ECDSA_SECP256R1_SHA256] and [ECDSA_SECP256K1_SHA256].
     * @param signatureScheme a supported [SignatureScheme], see [Crypto].
     * @param entropy a [BigInteger] value.
     * @return a new [KeyPair] from an entropy input.
     * @throws IllegalArgumentException if the requested signature scheme is not supported for KeyPair generation using an entropy input.
     */
    @DeleteForDJVM
    @JvmStatic
    fun deriveKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        return when (signatureScheme) {
            EDDSA_ED25519_SHA512 -> deriveEdDSAKeyPairFromEntropy(entropy)
            ECDSA_SECP256R1_SHA256, ECDSA_SECP256K1_SHA256 -> deriveECDSAKeyPairFromEntropy(signatureScheme, entropy)
            else -> throw IllegalArgumentException("Unsupported signature scheme for fixed entropy-based key pair " +
                    "generation: ${signatureScheme.schemeCodeName}")
        }
    }

    /**
     * Returns a [DEFAULT_SIGNATURE_SCHEME] key pair derived from the given [BigInteger] entropy.
     * @param entropy a [BigInteger] value.
     * @return a new [KeyPair] from an entropy input.
     */
    @DeleteForDJVM
    @JvmStatic
    fun deriveKeyPairFromEntropy(entropy: BigInteger): KeyPair = deriveKeyPairFromEntropy(DEFAULT_SIGNATURE_SCHEME, entropy)

    // Custom key pair generator from entropy.
    // The BigIntenger.toByteArray() uses the two's-complement representation.
    // The entropy is transformed to a byte array in big-endian byte-order and
    // only the first ed25519.field.getb() / 8 bytes are used.
    private fun deriveEdDSAKeyPairFromEntropy(entropy: BigInteger): KeyPair {
        val params = EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec
        val bytes = entropy.toByteArray().copyOf(params.curve.field.getb() / 8) // Need to pad the entropy to the valid seed length.
        val priv = EdDSAPrivateKeySpec(bytes, params)
        val pub = EdDSAPublicKeySpec(priv.a, params)
        return KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
    }

    // Custom key pair generator from an entropy required for various tests. It is similar to deriveKeyPairECDSA,
    // but the accepted range of the input entropy is more relaxed:
    // 2 <= entropy < N, where N is the order of base-point G.
    private fun deriveECDSAKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        val parameterSpec = signatureScheme.algSpec as ECNamedCurveParameterSpec

        // The entropy might be a negative number and/or out of range (e.g. PRNG output).
        // In such cases we retry with hash(currentEntropy).
        while (entropy < ECConstants.TWO || entropy >= parameterSpec.n) {
            return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256().bytes))
        }

        val privateKeySpec = ECPrivateKeySpec(entropy, parameterSpec)
        val priv = BCECPrivateKey("EC", privateKeySpec, BouncyCastleProvider.CONFIGURATION)

        val pointQ = FixedPointCombMultiplier().multiply(parameterSpec.g, entropy)
        while (pointQ.isInfinity) {
            // Instead of throwing an exception, we retry with hash(entropy).
            return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256().bytes))
        }
        val publicKeySpec = ECPublicKeySpec(pointQ, parameterSpec)
        val pub = BCECPublicKey("EC", publicKeySpec, BouncyCastleProvider.CONFIGURATION)

        return KeyPair(pub, priv)
    }

    // Compute the HMAC-SHA512 using a privateKey as the MAC_key and a seed ByteArray.
    private fun deriveHMAC(privateKey: PrivateKey, seed: ByteArray): ByteArray {
        // Compute hmac(privateKey, seed).
        val mac = Mac.getInstance("HmacSHA512", cordaBouncyCastleProvider)
        val keyData = when (privateKey) {
            is BCECPrivateKey -> privateKey.d.toByteArray()
            is EdDSAPrivateKey -> privateKey.geta()
            else -> throw InvalidKeyException("Key type ${privateKey.algorithm} is not supported for deterministic key derivation")
        }
        val key = SecretKeySpec(keyData, "HmacSHA512")
        mac.init(key)
        return mac.doFinal(seed)
    }

    /**
     * Check if a point's coordinates are on the expected curve to avoid certain types of ECC attacks.
     * Point-at-infinity is not permitted as well.
     * @see <a href="https://safecurves.cr.yp.to/twist.html">Small subgroup and invalid-curve attacks</a> for a more descriptive explanation on such attacks.
     * We use this function on [validatePublicKey], which is currently used for signature verification only.
     * Thus, as these attacks are mostly not relevant to signature verification, we should note that
     * we are doing it out of an abundance of caution and specifically to proactively protect developers
     * against using these points as part of a DH key agreement or for use cases as yet unimagined.
     * This method currently applies to BouncyCastle's ECDSA (both R1 and K1 curves) and I2P's EdDSA (ed25519 curve).
     * @param publicKey a [PublicKey], usually used to validate a signer's public key in on the Curve.
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @return true if the point lies on the curve or false if it doesn't.
     * @throws IllegalArgumentException if the requested signature scheme or the key type is not supported.
     */
    @JvmStatic
    fun publicKeyOnCurve(signatureScheme: SignatureScheme, publicKey: PublicKey): Boolean {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        return when (publicKey) {
            is BCECPublicKey -> publicKey.parameters == signatureScheme.algSpec && !publicKey.q.isInfinity && publicKey.q.isValid
            is EdDSAPublicKey -> publicKey.params == signatureScheme.algSpec && !isEdDSAPointAtInfinity(publicKey) && publicKey.a.isOnCurve
            else -> throw IllegalArgumentException("Unsupported key type: ${publicKey::class}")
        }
    }

    // Return true if EdDSA publicKey is point at infinity.
    // For EdDSA a custom function is required as it is not supported by the I2P implementation.
    private fun isEdDSAPointAtInfinity(publicKey: EdDSAPublicKey): Boolean {
        return publicKey.a.toP3() == (EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec).curve.getZero(GroupElement.Representation.P3)
    }

    /** Check if the requested [SignatureScheme] is supported by the system. */
    @JvmStatic
    fun isSupportedSignatureScheme(signatureScheme: SignatureScheme): Boolean {
        return signatureScheme.schemeCodeName in signatureSchemeMap
    }

    /**
     * Check if a public key satisfies algorithm specs.
     * For instance, an ECC key should lie on the curve and not being point-at-infinity.
     */
    @JvmStatic
    fun validatePublicKey(key: PublicKey): Boolean = validatePublicKey(findSignatureScheme(key), key)

    // Validate a key, by checking its algorithmic params.
    private fun validateKey(signatureScheme: SignatureScheme, key: Key): Boolean {
        return when (key) {
            is PublicKey -> validatePublicKey(signatureScheme, key)
            is PrivateKey -> validatePrivateKey(signatureScheme, key)
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    // Check if a public key satisfies algorithm specs (for ECC: key should lie on the curve and not being point-at-infinity).
    private fun validatePublicKey(signatureScheme: SignatureScheme, key: PublicKey): Boolean {
        return when (key) {
            is BCECPublicKey, is EdDSAPublicKey -> publicKeyOnCurve(signatureScheme, key)
            is BCRSAPublicKey -> key.modulus.bitLength() >= 2048 // Although the recommended RSA key size is 3072, we accept any key >= 2048bits.
            is BCSphincs256PublicKey -> true
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    // Check if a private key satisfies algorithm specs.
    private fun validatePrivateKey(signatureScheme: SignatureScheme, key: PrivateKey): Boolean {
        return when (key) {
            is BCECPrivateKey -> key.parameters == signatureScheme.algSpec
            is EdDSAPrivateKey -> key.params == signatureScheme.algSpec
            is BCRSAPrivateKey, is BCSphincs256PrivateKey -> true // TODO: Check if non-ECC keys satisfy params (i.e. approved/valid RSA modulus size).
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    /**
     * Convert a public key to a supported implementation.
     * @param key a public key.
     * @return a supported implementation of the input public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    @JvmStatic
    fun toSupportedPublicKey(key: SubjectPublicKeyInfo): PublicKey = decodePublicKey(key.encoded)

    /**
     * Convert a public key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     * This method is usually required to retrieve a key (via its corresponding cert) from JKS keystores that by default
     * return SUN implementations.
     * @param key a public key.
     * @return a supported implementation of the input public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    @JvmStatic
    fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return when (key) {
            is BCECPublicKey -> key
            is BCRSAPublicKey -> key
            is BCSphincs256PublicKey -> key
            is EdDSAPublicKey -> key
            is CompositeKey -> key
            else -> decodePublicKey(key.encoded)
        }
    }

    /**
     * Convert a private key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     * This method is usually required to retrieve keys from JKS keystores that by default return SUN implementations.
     * @param key a private key.
     * @return a supported implementation of the input private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    @JvmStatic
    fun toSupportedPrivateKey(key: PrivateKey): PrivateKey {
        return when (key) {
            is BCECPrivateKey -> key
            is BCRSAPrivateKey -> key
            is BCSphincs256PrivateKey -> key
            is EdDSAPrivateKey -> key
            else -> decodePrivateKey(key.encoded)
        }
    }

    /**
     *  Get the hash value that is actually signed.
     *  The txId is returned when [partialMerkleTree] is null,
     *  else the root of the tree is computed and returned.
     *  Note that the hash of the txId should be a leaf in the tree, not the txId itself.
     */
    private fun originalSignedHash(txId: SecureHash, partialMerkleTree: PartialMerkleTree?): SecureHash {
        return if (partialMerkleTree != null) {
            val usedHashes = mutableListOf<SecureHash>()
            val root = PartialMerkleTree.rootAndUsedHashes(partialMerkleTree.root, usedHashes)
            require(txId.sha256() in usedHashes) { "Transaction with id:$txId is not a leaf in the provided partial Merkle tree" }
            root
        } else {
            txId
        }
    }

    /**
     * Method to force registering all [Crypto]-related cryptography [Provider]s.
     * It is recommended that it is invoked first thing on `main` functions, so the [Provider]s are in place before any
     * cryptographic operation is requested outside [Crypto] (i.e., SecureRandom, KeyStore, cert-path validation,
     * CRL & CSR checks etc.).
     */
    // TODO: perform all cryptographic operations via Crypto.
    @JvmStatic
    fun registerProviders() {
        providerMap
    }
}
