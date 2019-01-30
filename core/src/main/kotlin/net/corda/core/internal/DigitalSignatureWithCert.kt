package net.corda.core.internal

import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.verify
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.OpaqueBytes
import java.security.cert.*

// TODO: Rename this to DigitalSignature.WithCert once we're happy for it to be public API. The methods will need documentation
// and the correct exceptions will be need to be annotated
/** A digital signature with attached certificate of the public key and (optionally) the remaining chain of the certificates from the certificate path. */
class DigitalSignatureWithCert(val by: X509Certificate, val parentCertsChain: List<X509Certificate>, bytes: ByteArray) : DigitalSignature(bytes) {
    @DeprecatedConstructorForDeserialization(1)
    constructor(by: X509Certificate, bytes: ByteArray) : this(by, emptyList(), bytes)

    val fullCertChain: List<X509Certificate> get() = listOf(by) + parentCertsChain
    val fullCertPath: CertPath get() = CertificateFactory.getInstance("X.509").generateCertPath(fullCertChain)

    fun verify(content: ByteArray): Boolean = by.publicKey.verify(content, this)
    fun verify(content: OpaqueBytes): Boolean = verify(content.bytes)

    init {
        if (parentCertsChain.isNotEmpty()) {
            val parameters = PKIXParameters(setOf(TrustAnchor(parentCertsChain.last(), null))).apply { isRevocationEnabled = false }
            try {
                CertPathValidator.getInstance("PKIX").validate(fullCertPath, parameters)
            } catch (e: CertPathValidatorException) {
                throw IllegalArgumentException(
                        """Cert path failed to validate.
Reason: ${e.reason}
Offending cert index: ${e.index}
Cert path: $fullCertPath
""", e)
            }
        }

    }
}

/** Similar to [SignedData] but instead of just attaching the public key, the certificate for the key is attached instead. */
@CordaSerializable
class SignedDataWithCert<T : Any>(val raw: SerializedBytes<T>, val sig: DigitalSignatureWithCert): NamedByHash {
    override val id: SecureHash get () = raw.hash

    fun verified(): T {
        sig.verify(raw)
        return uncheckedCast(raw.deserialize<Any>())
    }
}