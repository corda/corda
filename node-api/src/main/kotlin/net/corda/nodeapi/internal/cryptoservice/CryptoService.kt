package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.DoNotImplement
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey

/**
 * Unlike [CryptoService] can only perform "read-only" operations but never create new key pairs.
 */
@DoNotImplement
interface SignOnlyCryptoService {
    /** Check if this [CryptoService] has a private key entry for the input alias. */
    fun containsKey(alias: String): Boolean

    /**
     * Returns the [PublicKey] of the input alias or null if it doesn't exist.
     */
    fun getPublicKey(alias: String): PublicKey?

    /**
     * Sign a [ByteArray] using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme.
     * The signAlgorithm if specified determines the signature scheme used for signing, if
     * not specified then the signature scheme is based on the private key scheme.
     */
    fun sign(alias: String, data: ByteArray, signAlgorithm: String? = null): ByteArray

    /**
     * Returns [ContentSigner] for the key identified by the input alias.
     */
    fun getSigner(alias: String): ContentSigner

    /**
     * Returns the [SignatureScheme] that should be used for generating key pairs for the node's legal identity with this [CryptoService].
     */
    fun defaultIdentitySignatureScheme(): SignatureScheme = X509Utilities.DEFAULT_IDENTITY_SIGNATURE_SCHEME

    /**
     * Returns the [SignatureScheme] that should be used with this [CryptoService] when generating TLS-compatible key pairs.
     */
    fun defaultTLSSignatureScheme(): SignatureScheme = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
}

/**
 * Fully-powered crypto service which can sign as well as create new key pairs.
 */
@DoNotImplement
interface CryptoService : SignOnlyCryptoService {

    /**
     * Generate and store a new [KeyPair].
     * Note that schemeNumberID is Corda specific. Cross-check with the network operator for supported schemeNumberID
     * and their corresponding signature schemes. The main reason for using schemeNumberID and not algorithm OIDs is
     * because some schemes might not be standardised and thus an official OID might for this scheme not exist yet.
     *
     * Returns the [PublicKey] of the generated [KeyPair].
     */
    fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey


    // ******************************************************
    // ENTERPRISE ONLY CODE FOR WRAPPING KEYS API STARTS HERE
    // ******************************************************

    /**
     * Generates a new key to be used as a wrapping key.
     *
     * @param alias the alias under which the created wrapping key is created.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under the provided alias or return normally without overriding the key.
     *   The default value is true.
     *
     * @throws IllegalArgumentException if a key already exists under this alias (and [failIfExists] is set to true].
     */
    fun createWrappingKey(alias: String, failIfExists: Boolean = true)

    /**
     * The size of the wrapping key, as specified in the standard. See: https://csrc.nist.gov/publications/detail/sp/800-38f/final
     * All underlying implementations should use this key size, unless there is a specific reason for not doing so.
     */
    fun wrappingKeySize(): Int = 256

    /**
     * Generates an asymmetric key pair, returning the public key and the private key material wrapped using the specified wrapping key.
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param childKeyScheme the parameters of the key pair to be generated.
     *
     * @throws IllegalStateException if there is no master key existing under the alias specified ([masterKeyAlias]).
     */
    fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme = defaultWrappingSignatureScheme()): Pair<PublicKey, WrappedPrivateKey>

    /**
     * Unwraps the provided wrapped key, using the specified wrapping key and signs the provided payload.
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param wrappedPrivateKey the private key to be used for signing in a wrapped form.
     * @param payloadToSign the payload to be signed.
     *
     * @throws IllegalStateException if there is no master key existing under the alias specified ([masterKeyAlias]).
     */
    fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray

    /**
     * Returns the [WrappingMode] supported by the associated implementation.
     *
     * If no mode is supported, then null will be returned by this method and all the associated operations will throw an [UnsupportedOperationException].
     * Note: the long-term plan is to completely eradicate this case and have all implementations support one of the modes, even the degraded one.
     * As a result, this optionality is introduced for practical reasons and might be removed in the next major release,
     * when support will be added for all the existing implementations.
     */
    fun getWrappingMode(): WrappingMode?

    /**
     * Returns the [SignatureScheme] that should be used with this [CryptoService] when generating wrapped keys
     */
    fun defaultWrappingSignatureScheme(): SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256

    // *****************************************************
    // ENTERPRISE ONLY CODE FOR WRAPPING KEYS API ENDS HERE
    // *****************************************************
}

/**
 * If an exception is deemed unrecoverable then it must be set with the flag ``isRecoverable=false``
 *
 * [ManagedCryptoService] will assume any [Throwable] which isn't a [CryptoServiceException] is recoverable and
 * will wrap it in a [CryptoServiceException] with ``isRecoverable=true``.
*/
open class CryptoServiceException(message: String?, cause: Throwable? = null, val isRecoverable: Boolean = true) : Exception(message, cause)

enum class WrappingMode {
    /**
     * In degraded mode, wrapped keys' material is encrypted at rest, but it's temporarily exposed during key generation and signing.
     */
    DEGRADED_WRAPPED,
    /**
     * In normal mode, wrapped keys' material is encrypted, never exposed to the application and only accessible from inside the HSM.
     */
    WRAPPED
}

class WrappedPrivateKey(val keyMaterial: ByteArray, val signatureScheme: SignatureScheme, val encodingVersion: Int? = null)