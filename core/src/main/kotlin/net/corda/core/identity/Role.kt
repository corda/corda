package net.corda.core.identity

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence

enum class Role(val asn1Val: Long, val parent: Role?) : ASN1Encodable {
    NODE_CA(1, null),
    TLS(2, NODE_CA),
    WELL_KNOWN_IDENTITY(3, NODE_CA),
    CONFIDENTIAL_IDENTITY(4, WELL_KNOWN_IDENTITY);

    companion object {
        fun getInstance(obj: ASN1Encodable): Role {
            val ordinal = (obj as ASN1Integer).positiveValue
            return Role.values().get(ordinal.toInt() - 1)
        }
    }

    override fun toASN1Primitive(): ASN1Primitive = ASN1Integer(asn1Val)
}