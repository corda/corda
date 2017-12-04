package net.corda.core.crypto

import net.corda.core.identity.Role
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.X509Extension

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