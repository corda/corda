package net.corda.core.internal

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.verify
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.OpaqueBytes
import java.security.cert.CertPath
import java.security.cert.X509Certificate

// TODO: Rename this to DigitalSignature.WithCert once we're happy for it to be public API. The methods will need documentation
// and the correct exceptions will be need to be annotated
/** A digital signature with attached certificate of the public key. */
open class DigitalSignatureWithCert(val by: X509Certificate, bytes: ByteArray) : DigitalSignature(bytes) {
    fun verify(content: ByteArray): Boolean = by.publicKey.verify(content, this)
    fun verify(content: OpaqueBytes): Boolean = verify(content.bytes)
}

/**
 * A digital signature with attached certificate path. The first certificate in the path corresponds to the data signer key.
 * @param path certificate path associated with this signature
 * @param bytes signature bytes
 */
class DigitalSignatureWithCertPath(val path: List<X509Certificate>, bytes: ByteArray): DigitalSignatureWithCert(path.first(), bytes)

/** Similar to [SignedData] but instead of just attaching the public key, the certificate for the key is attached instead. */
@CordaSerializable
class SignedDataWithCert<T : Any>(val raw: SerializedBytes<T>, val sig: DigitalSignatureWithCert) {
    fun verified(): T {
        sig.verify(raw)
        return uncheckedCast(raw.deserialize<Any>())
    }
}