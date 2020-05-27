package sandbox.net.corda.core.crypto

import sandbox.net.corda.core.crypto.DJVM.fromDJVM
import sandbox.net.corda.core.crypto.DJVM.toDJVM
import sandbox.java.lang.String
import sandbox.java.lang.doCatch
import sandbox.java.math.BigInteger
import sandbox.java.security.KeyPair
import sandbox.java.security.PrivateKey
import sandbox.java.security.PublicKey
import sandbox.java.util.ArrayList
import sandbox.java.util.List
import sandbox.java.lang.Object
import sandbox.org.bouncycastle.asn1.x509.AlgorithmIdentifier
import sandbox.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.GeneralSecurityException
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException

/**
 * This is a hand-written "drop-in" replacement for the version of
 * [net.corda.core.crypto.Crypto] found inside core-deterministic.
 * This class is used in the DJVM sandbox instead of transforming
 * the byte-code for [net.corda.core.crypto.Crypto], with the goal
 * of not needing to instantiate some EXPENSIVE elliptic curve
 * cryptography classes inside every single sandbox.
 *
 * The downside is that this class MUST manually be kept consistent
 * with the DJVM's byte-code rewriting rules.
 */
@Suppress("unused", "unused_parameter", "TooManyFunctions")
object Crypto : Object() {
    @JvmField
    val RSA_SHA256: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.RSA_SHA256)

    @JvmField
    val ECDSA_SECP256K1_SHA256: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.ECDSA_SECP256K1_SHA256)

    @JvmField
    val ECDSA_SECP256R1_SHA256: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.ECDSA_SECP256R1_SHA256)

    @JvmField
    val EDDSA_ED25519_SHA512: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.EDDSA_ED25519_SHA512)

    @JvmField
    val SPHINCS256_SHA256: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.SPHINCS256_SHA256)

    @JvmField
    val COMPOSITE_KEY: SignatureScheme = toDJVM(net.corda.core.crypto.Crypto.COMPOSITE_KEY)

    @JvmField
    val DEFAULT_SIGNATURE_SCHEME = EDDSA_ED25519_SHA512

    /**
     * We can use the unsandboxed versions of [Map] and [List] here
     * because the [underlyingSchemes] and [djvmSchemes] fields are
     * private and not exposed to the rest of the sandbox.
     */
    private val underlyingSchemes: Map<kotlin.String, net.corda.core.crypto.SignatureScheme>
        = net.corda.core.crypto.Crypto.supportedSignatureSchemes()
            .associateBy(net.corda.core.crypto.SignatureScheme::schemeCodeName)
    private val djvmSchemes: Map<String, SignatureScheme> = listOf(
        RSA_SHA256,
        ECDSA_SECP256K1_SHA256,
        ECDSA_SECP256R1_SHA256,
        EDDSA_ED25519_SHA512,
        SPHINCS256_SHA256,
        COMPOSITE_KEY
    ).associateByTo(LinkedHashMap(), SignatureScheme::schemeCodeName)

    private fun findUnderlyingSignatureScheme(signatureScheme: SignatureScheme): net.corda.core.crypto.SignatureScheme {
        return net.corda.core.crypto.Crypto.findSignatureScheme(String.fromDJVM(signatureScheme.schemeCodeName))
    }

    private fun PublicKey.toUnderlyingKey(): java.security.PublicKey {
        return (this as? DJVMPublicKey ?: throw sandbox.java.lang.fail("Unsupported key ${this::class.java.name}")).underlying
    }

    @JvmStatic
    fun supportedSignatureSchemes(): List<SignatureScheme> {
        val schemes = ArrayList<SignatureScheme>(djvmSchemes.size)
        for (scheme in djvmSchemes.values) {
            schemes.add(scheme)
        }
        return schemes
    }

    @JvmStatic
    fun isSupportedSignatureScheme(signatureScheme: SignatureScheme): Boolean {
        return String.fromDJVM(signatureScheme.schemeCodeName) in underlyingSchemes
    }

    @JvmStatic
    fun findSignatureScheme(schemeCodeName: String): SignatureScheme {
        return djvmSchemes[schemeCodeName]
            ?: throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $schemeCodeName")
    }

    @JvmStatic
    fun findSignatureScheme(schemeNumberID: Int): SignatureScheme {
        val underlyingScheme = net.corda.core.crypto.Crypto.findSignatureScheme(schemeNumberID)
        return findSignatureScheme(String.toDJVM(underlyingScheme.schemeCodeName))
    }

    @JvmStatic
    fun findSignatureScheme(key: PublicKey): SignatureScheme {
        val underlyingScheme = net.corda.core.crypto.Crypto.findSignatureScheme(key.toUnderlyingKey())
        return findSignatureScheme(String.toDJVM(underlyingScheme.schemeCodeName))
    }

    @JvmStatic
    fun findSignatureScheme(algorithm: AlgorithmIdentifier): SignatureScheme {
        val underlyingScheme = net.corda.core.crypto.Crypto.findSignatureScheme(fromDJVM(algorithm))
        return findSignatureScheme(String.toDJVM(underlyingScheme.schemeCodeName))
    }

    @JvmStatic
    fun findSignatureScheme(key: PrivateKey): SignatureScheme {
        throw sandbox.java.lang.failApi("Crypto.findSignatureScheme(PrivateKey)")
    }

    @JvmStatic
    fun decodePrivateKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PrivateKey {
        throw sandbox.java.lang.failApi("Crypto.decodePrivateKey(SignatureScheme, byte[])")
    }

    @JvmStatic
    fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        val underlying = try {
            net.corda.core.crypto.Crypto.decodePublicKey(encodedKey)
        } catch (e: InvalidKeySpecException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
        return DJVMPublicKey(underlying)
    }

    @JvmStatic
    fun decodePublicKey(schemeCodeName: String, encodedKey: ByteArray): PublicKey {
        val underlying = try {
            net.corda.core.crypto.Crypto.decodePublicKey(String.fromDJVM(schemeCodeName), encodedKey)
        } catch (e: InvalidKeySpecException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
        return DJVMPublicKey(underlying)
    }

    @JvmStatic
    fun decodePublicKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PublicKey {
        return decodePublicKey(signatureScheme.schemeCodeName, encodedKey)
    }

    @JvmStatic
    fun deriveKeyPair(signatureScheme: SignatureScheme, privateKey: PrivateKey, seed: ByteArray): KeyPair {
        throw sandbox.java.lang.failApi("Crypto.deriveKeyPair(SignatureScheme, PrivateKey, byte[])")
    }

    @JvmStatic
    fun deriveKeyPair(privateKey: PrivateKey, seed: ByteArray): KeyPair {
        throw sandbox.java.lang.failApi("Crypto.deriveKeyPair(PrivateKey, byte[])")
    }

    @JvmStatic
    fun deriveKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        throw sandbox.java.lang.failApi("Crypto.deriveKeyPairFromEntropy(SignatureScheme, BigInteger)")
    }

    @JvmStatic
    fun deriveKeyPairFromEntropy(entropy: BigInteger): KeyPair {
        throw sandbox.java.lang.failApi("Crypto.deriveKeyPairFromEntropy(BigInteger)")
    }

    @JvmStatic
    fun doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val underlyingKey = publicKey.toUnderlyingKey()
        return try {
            net.corda.core.crypto.Crypto.doVerify(String.fromDJVM(schemeCodeName), underlyingKey, signatureData, clearData)
        } catch (e: GeneralSecurityException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
    }

    @JvmStatic
    fun doVerify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val underlyingKey = publicKey.toUnderlyingKey()
        return try {
            net.corda.core.crypto.Crypto.doVerify(underlyingKey, signatureData, clearData)
        } catch (e: GeneralSecurityException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
    }

    @JvmStatic
    fun doVerify(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val underlyingScheme = findUnderlyingSignatureScheme(signatureScheme)
        val underlyingKey = publicKey.toUnderlyingKey()
        return try {
            net.corda.core.crypto.Crypto.doVerify(underlyingScheme, underlyingKey, signatureData, clearData)
        } catch (e: GeneralSecurityException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
    }

    @JvmStatic
    fun doVerify(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        throw sandbox.java.lang.failApi("Crypto.doVerify(SecureHash, TransactionSignature)")
    }

    @JvmStatic
    fun isValid(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        throw sandbox.java.lang.failApi("Crypto.isValid(SecureHash, TransactionSignature)")
    }

    @JvmStatic
    fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val underlyingKey = publicKey.toUnderlyingKey()
        return try {
            net.corda.core.crypto.Crypto.isValid(underlyingKey, signatureData, clearData)
        } catch (e: SignatureException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
    }

    @JvmStatic
    fun isValid(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        val underlyingScheme = findUnderlyingSignatureScheme(signatureScheme)
        val underlyingKey = publicKey.toUnderlyingKey()
        return try {
            net.corda.core.crypto.Crypto.isValid(underlyingScheme, underlyingKey, signatureData, clearData)
        } catch (e: SignatureException) {
            throw sandbox.java.lang.fromDJVM(doCatch(e))
        }
    }

    @JvmStatic
    fun publicKeyOnCurve(signatureScheme: SignatureScheme, publicKey: PublicKey): Boolean {
        val underlyingScheme = findUnderlyingSignatureScheme(signatureScheme)
        val underlyingKey = publicKey.toUnderlyingKey()
        return net.corda.core.crypto.Crypto.publicKeyOnCurve(underlyingScheme, underlyingKey)
    }

    @JvmStatic
    fun validatePublicKey(key: PublicKey): Boolean {
        return net.corda.core.crypto.Crypto.validatePublicKey(key.toUnderlyingKey())
    }

    @JvmStatic
    fun toSupportedPublicKey(key: SubjectPublicKeyInfo): PublicKey {
        return decodePublicKey(key.encoded)
    }

    @JvmStatic
    fun toSupportedPublicKey(key: PublicKey): PublicKey {
        val underlyingKey = key.toUnderlyingKey()
        val supportedKey = net.corda.core.crypto.Crypto.toSupportedPublicKey(underlyingKey)
        return if (supportedKey === underlyingKey) {
            key
        } else {
            DJVMPublicKey(supportedKey)
        }
    }
}
