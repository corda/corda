package net.corda.core.crypto

import net.corda.core.CordaOID
import net.corda.core.identity.Role
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.X509Extension
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * X.509 certificate extension for the Corda role the certificate represents.
 */
class IdentityRoleExtension : ASN1Encodable {
    companion object {
        fun getInstance(obj: Any?): IdentityRoleExtension? {
            return when (obj) {
                null -> null
                is IdentityRoleExtension -> obj
                is X509Extension -> getInstance(X509Extension.convertValueToObject(obj))
                else -> IdentityRoleExtension(ASN1Sequence.getInstance(obj))
            }
        }
        fun get(cert: Certificate): IdentityRoleExtension? {
            val x509Cert = cert as? X509Certificate
            return if (x509Cert != null) {
                get(x509Cert)
            } else {
                null
            }
        }
        fun get(cert: X509Certificate): IdentityRoleExtension? {
            val extensionData: ByteArray? = cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE)
            return if (extensionData != null) {
                val extensionString = DEROctetString.getInstance(extensionData)
                IdentityRoleExtension.getInstance(extensionString.octets)
            } else {
                null
            }
        }
    }

    val role: Role
    constructor(role: Role) {
        this.role = role
    }
    constructor(sequence: ASN1Sequence) {
        require(sequence.size() == 1)
        this.role = Role.getInstance(sequence.getObjectAt(0))
    }

    override fun toASN1Primitive(): ASN1Primitive {
        return DERSequence(ASN1EncodableVector().apply {
            add(role)
        })
    }
}