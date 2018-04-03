package net.corda.core.internal

import net.corda.annotations.serialization.Serializable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.verify
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.OpaqueBytes
import java.security.cert.X509Certificate

// TODO: Rename this to DigitalSignature.WithCert once we're happy for it to be public API. The methods will need documentation
// and the correct exceptions will be need to be annotated
/** A digital signature with attached certificate of the public key. */
class DigitalSignatureWithCert(val by: X509Certificate, bytes: ByteArray) : DigitalSignature(bytes) {
    fun verify(content: ByteArray): Boolean = by.publicKey.verify(content, this)
    fun verify(content: OpaqueBytes): Boolean = verify(content.bytes)
}

/** Similar to [SignedData] but instead of just attaching the public key, the certificate for the key is attached instead. */
@Serializable
class SignedDataWithCert<T : Any>(val raw: SerializedBytes<T>, val sig: DigitalSignatureWithCert) {
    fun verified(): T {
        sig.verify(raw)
        return uncheckedCast(raw.deserialize<Any>())
    }
}