package net.corda.core.crypto

import net.corda.core.random63BitValue
import net.i2p.crypto.eddsa.*
import net.i2p.crypto.eddsa.math.GroupElement
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import sun.security.pkcs.PKCS8Key
import sun.security.util.DerValue
import sun.security.x509.X509Key
import java.math.BigInteger
import java.security.*
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.cert.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

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
    val RSA_SHA256 = SignatureScheme(
            1,
            "RSA_SHA256",
            PKCSObjectIdentifiers.id_RSASSA_PSS,
            BouncyCastleProvider.PROVIDER_NAME,
            "RSA",
            "SHA256WITHRSAANDMGF1",
            null,
            3072,
            "RSA_SHA256 signature scheme using SHA256 as hash algorithm and MGF1 (with SHA256) as mask generation function."
    )

    /** ECDSA signature scheme using the secp256k1 Koblitz curve. */
    val ECDSA_SECP256K1_SHA256 = SignatureScheme(
            2,
            "ECDSA_SECP256K1_SHA256",
            X9ObjectIdentifiers.ecdsa_with_SHA256,
            BouncyCastleProvider.PROVIDER_NAME,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256k1"),
            256,
            "ECDSA signature scheme using the secp256k1 Koblitz curve."
    )

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    val ECDSA_SECP256R1_SHA256 = SignatureScheme(
            3,
            "ECDSA_SECP256R1_SHA256",
            X9ObjectIdentifiers.ecdsa_with_SHA256,
            BouncyCastleProvider.PROVIDER_NAME,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            256,
            "ECDSA signature scheme using the secp256r1 (NIST P-256) curve."
    )

    /** EdDSA signature scheme using the ed255519 twisted Edwards curve. */
    val EDDSA_ED25519_SHA512 = SignatureScheme(
            4,
            "EDDSA_ED25519_SHA512",
            ASN1ObjectIdentifier("1.3.101.112"),
            // We added EdDSA to bouncy castle for certificate signing.
            BouncyCastleProvider.PROVIDER_NAME,
            EdDSAKey.KEY_ALGORITHM,
            EdDSAEngine.SIGNATURE_ALGORITHM,
            EdDSANamedCurveTable.getByName("ED25519"),
            256,
            "EdDSA signature scheme using the ed25519 twisted Edwards curve."
    )

    /**
     * SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers
     * at the cost of larger key sizes and loss of compatibility.
     */
    val SPHINCS256_SHA256 = SignatureScheme(
            5,
            "SPHINCS-256_SHA512",
            BCObjectIdentifiers.sphincs256_with_SHA512,
            "BCPQC",
            "SPHINCS256",
            "SHA512WITHSPHINCS256",
            SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
            256,
            "SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers " +
                    "at the cost of larger key sizes and loss of compatibility."
    )

    /** Our default signature scheme if no algorithm is specified (e.g. for key generation). */
    val DEFAULT_SIGNATURE_SCHEME = EDDSA_ED25519_SHA512

    /**
     * Supported digital signature schemes.
     * Note: Only the schemes added in this map will be supported (see [Crypto]).
     */
    val supportedSignatureSchemes = listOf(
            RSA_SHA256,
            ECDSA_SECP256K1_SHA256,
            ECDSA_SECP256R1_SHA256,
            EDDSA_ED25519_SHA512,
            SPHINCS256_SHA256
    ).associateBy { it.schemeCodeName }

    // We need to group signature schemes per algorithm, so to quickly identify them during decoding.
    // Please note there are schemes with the same algorithm, e.g. EC (or ECDSA) keys are used for both ECDSA_SECP256K1_SHA256 and ECDSA_SECP256R1_SHA256.
    private val algorithmGroups = supportedSignatureSchemes.values.groupBy { it.algorithmName }

    // This map is required to defend against users that forcibly call Security.addProvider / Security.removeProvider
    // that could cause unexpected and suspicious behaviour.
    // i.e. if someone removes a Provider and then he/she adds a new one with the same name.
    // The val is private to avoid any harmful state changes.
    private val providerMap: Map<String, Provider> = mapOf(
            BouncyCastleProvider.PROVIDER_NAME to getBouncyCastleProvider(),
            "BCPQC" to BouncyCastlePQCProvider()) // unfortunately, provider's name is not final in BouncyCastlePQCProvider, so we explicitly set it.

    private fun getBouncyCastleProvider() = BouncyCastleProvider().apply {
        putAll(EdDSASecurityProvider())
        addKeyInfoConverter(EDDSA_ED25519_SHA512.signatureOID, KeyInfoConverter(EDDSA_ED25519_SHA512))
    }

    init {
        // This registration is needed for reading back EdDSA key from java keystore.
        // TODO: Find a way to make JKS work with bouncy castle provider or implement our own provide so we don't have to register bouncy castle provider.
        Security.addProvider(getBouncyCastleProvider())
    }

    /**
     * Factory pattern to retrieve the corresponding [SignatureScheme] based on the type of the [String] input.
     * This function is usually called by key generators and verify signature functions.
     * In case the input is not a key in the supportedSignatureSchemes map, null will be returned.
     * @param schemeCodeName a [String] that should match a supported signature scheme code name (e.g. ECDSA_SECP256K1_SHA256), see [Crypto].
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    fun findSignatureScheme(schemeCodeName: String): SignatureScheme = supportedSignatureSchemes[schemeCodeName] ?: throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $schemeCodeName")

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [Key].
     * This function is usually called when requiring to verify signatures and the signing schemes must be defined.
     * For the supported signature schemes see [Crypto].
     * @param key either private or public.
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findSignatureScheme(key: Key): SignatureScheme {
        val algorithm = matchingAlgorithmName(key.algorithm)
        algorithmGroups[algorithm]?.filter { validateKey(it, key) }?.firstOrNull { return it }
        throw IllegalArgumentException("Unsupported key algorithm: ${key.algorithm} or invalid key format")
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object.
     * Use this method if the key type is a-priori unknown.
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class)
    fun decodePrivateKey(encodedKey: ByteArray): PrivateKey {
        val algorithm = matchingAlgorithmName(PKCS8Key.parseKey(DerValue(encodedKey)).algorithm)
        // There are cases where the same key algorithm is applied to different signature schemes.
        // Currently, this occurs with ECDSA as it applies to either secp256K1 or secp256R1 curves.
        // In such a case, we should try and identify which of the candidate schemes is the correct one so as
        // to generate the appropriate key.
        for (signatureScheme in algorithmGroups[algorithm]!!) {
            try {
                return KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName]).generatePrivate(PKCS8EncodedKeySpec(encodedKey))
            } catch (ikse: InvalidKeySpecException) {
                // ignore it - only used to bypass the scheme that causes an exception, as it has the same name, but different params.
            }
        }
        throw IllegalArgumentException("This private key cannot be decoded, please ensure it is PKCS8 encoded and the signature scheme is supported.")
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during Kryo deserialisation or with key caches or key managers.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePrivateKey(schemeCodeName: String, encodedKey: ByteArray): PrivateKey = decodePrivateKey(findSignatureScheme(schemeCodeName), encodedKey)

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during Kryo deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePrivateKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PrivateKey {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
        try {
            return KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName]).generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw InvalidKeySpecException("This private key cannot be decoded, please ensure it is PKCS8 encoded and that it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Decode an X509 encoded key to its [PublicKey] object.
     * Use this method if the key type is a-priori unknown.
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @Throws(IllegalArgumentException::class)
    fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        val algorithm = matchingAlgorithmName(X509Key.parse(DerValue(encodedKey)).algorithm)
        // There are cases where the same key algorithm is applied to different signature schemes.
        // Currently, this occurs with ECDSA as it applies to either secp256K1 or secp256R1 curves.
        // In such a case, we should try and identify which of the candidate schemes is the correct one so as
        // to generate the appropriate key.
        for (signatureScheme in algorithmGroups[algorithm]!!) {
            try {
                return KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName]).generatePublic(X509EncodedKeySpec(encodedKey))
            } catch (ikse: InvalidKeySpecException) {
                // ignore it - only used to bypass the scheme that causes an exception, as it has the same name, but different params.
            }
        }
        throw IllegalArgumentException("This public key cannot be decoded, please ensure it is X509 encoded and the signature scheme is supported.")
    }

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during Kryo deserialisation or with key caches or key managers.
     * @param schemeCodeName a [String] that should match a key in supportedSignatureSchemes map (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException if the requested scheme is not supported.
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePublicKey(schemeCodeName: String, encodedKey: ByteArray): PublicKey = decodePublicKey(findSignatureScheme(schemeCodeName), encodedKey)

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during Kryo deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException if the requested scheme is not supported.
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @Throws(IllegalArgumentException::class, InvalidKeySpecException::class)
    fun decodePublicKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PublicKey {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
        try {
            return KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName]).generatePublic(X509EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw throw InvalidKeySpecException("This public key cannot be decoded, please ensure it is X509 encoded and that it corresponds to the input scheme's code name.", ikse)
        }
    }

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
    fun doSign(privateKey: PrivateKey, clearData: ByteArray) = doSign(findSignatureScheme(privateKey), privateKey, clearData)

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
    fun doSign(schemeCodeName: String, privateKey: PrivateKey, clearData: ByteArray) = doSign(findSignatureScheme(schemeCodeName), privateKey, clearData)

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
    @Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
    fun doSign(signatureScheme: SignatureScheme, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
        val signature = Signature.getInstance(signatureScheme.signatureName, providerMap[signatureScheme.providerName])
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
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) = doVerify(findSignatureScheme(schemeCodeName), publicKey, signatureData, clearData)

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
    fun doVerify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) = doVerify(findSignatureScheme(publicKey), publicKey, signatureData, clearData)

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
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun doVerify(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
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
        if (publicKey != transactionSignature.metaData.publicKey) IllegalArgumentException("MetaData's publicKey: ${transactionSignature.metaData.publicKey.toStringShort()} does not match")
        return Crypto.doVerify(publicKey, transactionSignature.signatureData, transactionSignature.metaData.bytes())
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the input public key's type.
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
    @Throws(SignatureException::class)
    fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) = isValid(findSignatureScheme(publicKey), publicKey, signatureData, clearData)

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
    @Throws(SignatureException::class, IllegalArgumentException::class)
    fun isValid(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
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
    @Throws(IllegalArgumentException::class)
    fun generateKeyPair(schemeCodeName: String): KeyPair = generateKeyPair(findSignatureScheme(schemeCodeName))

    /**
     * Generate a [KeyPair] for the selected [SignatureScheme].
     * Note that RSA is the sole algorithm initialized specifically by its supported keySize.
     * @param signatureScheme a supported [SignatureScheme], see [Crypto], default to [DEFAULT_SIGNATURE_SCHEME] if not provided.
     * @return a new [KeyPair] for the requested [SignatureScheme].
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @Throws(IllegalArgumentException::class)
    @JvmOverloads
    fun generateKeyPair(signatureScheme: SignatureScheme = DEFAULT_SIGNATURE_SCHEME): KeyPair {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $signatureScheme.schemeCodeName")
        val keyPairGenerator = KeyPairGenerator.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
        if (signatureScheme.algSpec != null)
            keyPairGenerator.initialize(signatureScheme.algSpec, newSecureRandom())
        else
            keyPairGenerator.initialize(signatureScheme.keySize, newSecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Returns a key pair derived from the given [BigInteger] entropy. This is useful for unit tests
     * and other cases where you want hard-coded private keys.
     * Currently, [EDDSA_ED25519_SHA512] is the sole scheme supported for this operation.
     * @param signatureScheme a supported [SignatureScheme], see [Crypto].
     * @param entropy a [BigInteger] value.
     * @return a new [KeyPair] from an entropy input.
     * @throws IllegalArgumentException if the requested signature scheme is not supported for KeyPair generation using an entropy input.
     */
    fun generateKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        when (signatureScheme) {
            EDDSA_ED25519_SHA512 -> return generateEdDSAKeyPairFromEntropy(entropy)
        }
        throw IllegalArgumentException("Unsupported signature scheme for fixed entropy-based key pair generation: $signatureScheme.schemeCodeName")
    }

    /**
     * Returns a [DEFAULT_SIGNATURE_SCHEME] key pair derived from the given [BigInteger] entropy.
     * @param entropy a [BigInteger] value.
     * @return a new [KeyPair] from an entropy input.
     */
    fun generateKeyPairFromEntropy(entropy: BigInteger): KeyPair = generateKeyPairFromEntropy(DEFAULT_SIGNATURE_SCHEME, entropy)

    // custom key pair generator from entropy.
    private fun generateEdDSAKeyPairFromEntropy(entropy: BigInteger): KeyPair {
        val params = EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec
        val bytes = entropy.toByteArray().copyOf(params.curve.field.getb() / 8) // need to pad the entropy to the valid seed length.
        val priv = EdDSAPrivateKeySpec(bytes, params)
        val pub = EdDSAPublicKeySpec(priv.a, params)
        return KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
    }

    /**
     * Use bouncy castle utilities to sign completed X509 certificate with CA cert private key.
     */
    fun createCertificate(issuer: X500Name, issuerKeyPair: KeyPair,
                          subject: X500Name, subjectPublicKey: PublicKey,
                          keyUsage: KeyUsage, purposes: List<KeyPurposeId>,
                          validityWindow: Pair<Date, Date>,
                          subjectAlternativeName: List<GeneralName>? = null,
                          nameConstraints: NameConstraints? = null,
                          isCA: Boolean = true): X509Certificate {

        val signatureScheme = findSignatureScheme(issuerKeyPair.private)
        val provider = providerMap[signatureScheme.providerName]
        val serial = BigInteger.valueOf(random63BitValue())
        val keyPurposes = DERSequence(ASN1EncodableVector().apply { purposes.forEach { add(it) } })

        val builder = JcaX509v3CertificateBuilder(issuer, serial, validityWindow.first, validityWindow.second, subject, subjectPublicKey)
                .addExtension(Extension.subjectKeyIdentifier, false, BcX509ExtensionUtils().createSubjectKeyIdentifier(SubjectPublicKeyInfo.getInstance(subjectPublicKey.encoded)))
                .addExtension(Extension.basicConstraints, isCA, BasicConstraints(isCA))
                .addExtension(Extension.keyUsage, false, keyUsage)
                .addExtension(Extension.extendedKeyUsage, false, keyPurposes)

        if (subjectAlternativeName != null && subjectAlternativeName.isNotEmpty()) {
            builder.addExtension(Extension.subjectAlternativeName, false, DERSequence(subjectAlternativeName.toTypedArray()))
        }

        if (nameConstraints != null) {
            builder.addExtension(Extension.nameConstraints, true, nameConstraints)
        }

        val signer = ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
        return JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer)).apply {
            checkValidity(Date())
            verify(issuerKeyPair.public, provider)
        }
    }

    /**
     * Create certificate signing request using provided information.
     */
    fun createCertificateSigningRequest(subject: X500Name, keyPair: KeyPair, signatureScheme: SignatureScheme): PKCS10CertificationRequest {
        val signer = ContentSignerBuilder.build(signatureScheme, keyPair.private, providerMap[signatureScheme.providerName])
        return JcaPKCS10CertificationRequestBuilder(subject, keyPair.public).build(signer)
    }

    private class KeyInfoConverter(val signatureScheme: SignatureScheme) : AsymmetricKeyInfoConverter {
        override fun generatePublic(keyInfo: SubjectPublicKeyInfo?): PublicKey? = keyInfo?.let { decodePublicKey(signatureScheme, it.encoded) }
        override fun generatePrivate(keyInfo: PrivateKeyInfo?): PrivateKey? = keyInfo?.let { decodePrivateKey(signatureScheme, it.encoded) }
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
    @Throws(IllegalArgumentException::class)
    fun publicKeyOnCurve(signatureScheme: SignatureScheme, publicKey: PublicKey): Boolean {
        if (!isSupportedSignatureScheme(signatureScheme))
            throw IllegalArgumentException("Unsupported signature scheme: $signatureScheme.schemeCodeName")
        when (publicKey) {
            is BCECPublicKey -> return (publicKey.parameters == signatureScheme.algSpec && !publicKey.q.isInfinity && publicKey.q.isValid)
            is EdDSAPublicKey -> return (publicKey.params == signatureScheme.algSpec && !isEdDSAPointAtInfinity(publicKey) && publicKey.a.isOnCurve)
            else -> throw IllegalArgumentException("Unsupported key type: ${publicKey::class}")
        }
    }

    // return true if EdDSA publicKey is point at infinity.
    // For EdDSA a custom function is required as it is not supported by the I2P implementation.
    private fun isEdDSAPointAtInfinity(publicKey: EdDSAPublicKey) = publicKey.a.toP3() == (EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec).curve.getZero(GroupElement.Representation.P3)

    /** Check if the requested [SignatureScheme] is supported by the system. */
    fun isSupportedSignatureScheme(signatureScheme: SignatureScheme): Boolean = supportedSignatureSchemes[signatureScheme.schemeCodeName] === signatureScheme

    // map algorithm names returned from Keystore (or after encode/decode) to the supported algorithm names.
    private fun matchingAlgorithmName(algorithm: String): String {
        return when (algorithm) {
            "EC" -> "ECDSA"
            "SPHINCS-256" -> "SPHINCS256"
            "1.3.6.1.4.1.22554.2.1" -> "SPHINCS256" // Unfortunately, PKCS8Key and X509Key parsing return the OID as the algorithm name and not SPHINCS256.
            else -> algorithm
        }
    }

    // validate a key, by checking its algorithmic params.
    private fun validateKey(signatureScheme: SignatureScheme, key: Key): Boolean {
        return when (key) {
            is PublicKey -> validatePublicKey(signatureScheme, key)
            is PrivateKey -> validatePrivateKey(signatureScheme, key)
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    // check if a public key satisfies algorithm specs (for ECC: key should lie on the curve and not being point-at-infinity).
    private fun validatePublicKey(signatureScheme: SignatureScheme, key: PublicKey): Boolean {
        when (key) {
            is BCECPublicKey, is EdDSAPublicKey -> return publicKeyOnCurve(signatureScheme, key)
            is BCRSAPublicKey, is BCSphincs256PublicKey -> return true // TODO: Check if non-ECC keys satisfy params (i.e. approved/valid RSA modulus size).
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    // check if a private key satisfies algorithm specs.
    private fun validatePrivateKey(signatureScheme: SignatureScheme, key: PrivateKey): Boolean {
        when (key) {
            is BCECPrivateKey -> return key.parameters == signatureScheme.algSpec
            is EdDSAPrivateKey -> return key.params == signatureScheme.algSpec
            is BCRSAPrivateKey, is BCSphincs256PrivateKey -> return true // TODO: Check if non-ECC keys satisfy params (i.e. approved/valid RSA modulus size).
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class}")
        }
    }

    /**
     * Convert a public key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     * This method is usually required to retrieve a key (via its corresponding cert) from JKS keystores that by default return SUN implementations.
     * @param key a public key.
     * @return a supported implementation of the input public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return Crypto.decodePublicKey(key.encoded)
    }

    /**
     * Convert a private key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     * This method is usually required to retrieve keys from JKS keystores that by default return SUN implementations.
     * @param key a private key.
     * @return a supported implementation of the input private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    fun toSupportedPrivateKey(key: PrivateKey): PrivateKey {
        return Crypto.decodePrivateKey(key.encoded)
    }
}
