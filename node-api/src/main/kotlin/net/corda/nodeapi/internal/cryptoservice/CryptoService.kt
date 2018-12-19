package net.corda.nodeapi.internal.cryptoservice

import net.corda.core.DoNotImplement
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey

@DoNotImplement
interface CryptoService {

    /**
     * Generate and store a new [KeyPair].
     * Note that schemeNumberID is Corda specific. Cross-check with the network operator for supported schemeNumberID
     * and their corresponding signature schemes. The main reason for using schemeNumberID and not algorithm OIDs is
     * because some schemes might not be standardised and thus an official OID might for this scheme not exist yet.
     *
     * Returns the [PublicKey] of the generated [KeyPair].
     */
    fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey

    /** Check if this [CryptoService] has a private key entry for the input alias. */
    fun containsKey(alias: String): Boolean

    /**
     * Returns the [PublicKey] of the input alias or null if it doesn't exist.
     */
    fun getPublicKey(alias: String): PublicKey?

    /**
     * Sign a [ByteArray] using the private key identified by the input alias.
     * Returns the signature bytes whose format depends on the underlying signature scheme and it should
     * be Java BouncyCastle compatible (i.e., ASN.1 DER-encoded for ECDSA).
     */
    fun sign(alias: String, data: ByteArray): ByteArray

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

open class CryptoServiceException(message: String?, cause: Throwable? = null) : Exception(message, cause)
