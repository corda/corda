package net.corda.core.crypto

import net.corda.core.CordaOID
import net.corda.core.identity.Role
import org.bouncycastle.asn1.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * X.509 certificate extension for the Corda role the certificate represents.
 */
data class IdentityRoleExtension(val role: Role) : ASN1Encodable {
    companion object {
        fun getInstance(data: ByteArray): IdentityRoleExtension? {
            val sequence = ASN1Sequence.getInstance(data)
            require(sequence.size() == 1)
            return IdentityRoleExtension(Role.getInstance(sequence.getObjectAt(0)))
        }

        fun extract(cert: Certificate): IdentityRoleExtension? {
            val x509Cert = cert as? X509Certificate
            return if (x509Cert != null) {
                extract(x509Cert)
            } else {
                null
            }
        }

        fun extract(cert: X509Certificate): IdentityRoleExtension? {
            val extensionData: ByteArray? = cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE)
            return if (extensionData != null) {
                val extensionString = DEROctetString.getInstance(extensionData)
                IdentityRoleExtension.getInstance(extensionString.octets)
            } else {
                null
            }
        }
    }

    override fun toASN1Primitive(): ASN1Primitive {
        return DERSequence(ASN1EncodableVector().apply {
            add(role)
        })
    }
}