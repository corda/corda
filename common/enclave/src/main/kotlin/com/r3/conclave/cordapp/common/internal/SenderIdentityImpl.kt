package com.r3.conclave.cordapp.common.internal

import com.r3.conclave.common.internal.SignatureSchemeEdDSA
import com.r3.conclave.cordapp.common.SenderIdentity
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.PublicKey
import java.security.Security
import java.security.SignatureException
import java.security.cert.*

/**
 * Implements the [SenderIdentity] interface and allows a sender to prepare and send its identity to en Enclave, and
 * allows the Enclave to privately and securely verify the sender X509 name and public key belonging to a network where
 * parties are identified by a X509 certificate issued by the CA trusted by the Enclave.
 */
class SenderIdentityImpl(
    private val signerCertPath: CertPath,
    private val signatureData: ByteArray
) : SenderIdentity {
    private val signerCertificate: X509Certificate get() = (signerCertPath.certificates[0] as X509Certificate)

    init {
        require(signerCertPath.type == "X.509") { "Only X.509 certificates supported" }
        val certs = signerCertPath.certificates
        require(certs.size >= 2) { "Certificate path must at least include subject and issuing certificates" }
    }

    /**
     * Verifies the sender identified by the [signerCertificate] is the party that signed the [sharedSecret] producing
     * the [signatureData]
     */
    fun didSign(sharedSecret: ByteArray): Boolean {
        return try {
            signatureScheme.verify(signerCertificate.publicKey, signatureData, sharedSecret)
            true
        } catch (e: SignatureException) {
            false
        }
    }

    /**
     * Verifies the [signerCertPath] is valid and issued by the given trusted root
     *
     * @param caRoot the root certificate that is used to validate the instance's certificate path [signerCertPath]
     */
    fun isTrusted(caRoot: X509Certificate): Boolean {
        val trustAnchor = TrustAnchor(caRoot, null)
        val parameters = PKIXParameters(setOf(trustAnchor)).apply { isRevocationEnabled = false }
        return try {
            CertPathValidator.getInstance("PKIX").validate(signerCertPath, parameters)
            true
        } catch (e: CertPathValidatorException) {
            false
        }
    }

    /**
     * The verified subject name of the sender
     */
    override val name: String get() = signerCertificate.subjectX500Principal.name

    /**
     * The verified public key of the sender
     */
    override val publicKey: PublicKey get() = signerCertificate.publicKey

    /**
     * Serializes the [SenderIdentity] instance into a byte array.
     *
     * Used by the mail sender to serialize the [SenderIdentity] instance and privately send it to the Enclave message
     * handler where is then deserialized.
     */
    fun serialize(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        serialize(dos)

        return baos.toByteArray()
    }

    fun serialize(dos: DataOutputStream) {
        dos.writeIntLengthPrefixBytes(signerCertPath.getEncoded("PkiPath"))
        dos.writeIntLengthPrefixBytes(signatureData)
    }

    companion object {
        private val signatureScheme = SignatureSchemeEdDSA()

        init {
            Security.addProvider(EdDSASecurityProvider())
        }

        private fun deserializeCertPath(dis: DataInputStream): CertPath {
            val certBytes = dis.readIntLengthPrefixBytes()
            val certFactory = CertificateFactory.getInstance("X.509")
            return certFactory.generateCertPath(ByteArrayInputStream(certBytes), "PkiPath")
        }

        private fun deserializeSignedSecret(dis: DataInputStream): ByteArray = dis.readIntLengthPrefixBytes()

        /**
         * Deserialzes a serialized [SenderIdentityImpl] instance
         */
        fun deserialize(from: ByteArray): SenderIdentityImpl {
            val bais = ByteArrayInputStream(from)
            val dis = DataInputStream(bais)
            return deserialize(dis)
        }

        fun deserialize(dis: DataInputStream): SenderIdentityImpl {
            try {
                val signerCertPath = deserializeCertPath(dis)
                val signedSecret = deserializeSignedSecret(dis)

                return SenderIdentityImpl(signerCertPath, signedSecret)
            } catch (e: Exception) {
                throw IllegalArgumentException("Corrupted MailerIdentity bytes", e)
            }
        }
    }
}

private fun DataOutputStream.writeIntLengthPrefixBytes(data: ByteArray) {
    this.writeInt(data.size)
    this.write(data)
}

private fun DataInputStream.readIntLengthPrefixBytes(): ByteArray {
    val data = ByteArray(this.readInt())
    this.readFully(data)
    return data
}
