package net.corda.core.identity

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive

/**
 * Describes the Corda role a certificate is used for. This is used both to verify the hierarchy of certificates is
 * correct, and to determine which is the well known identity's certificate.
 */
enum class Role(val asn1Val: Long, val parent: Role?) : ASN1Encodable {
    /** The node level CA from which the TLS and well known identity certificates are issued. */
    NODE_CA(1, null),
    /** The transport layer security certificate */
    TLS(2, NODE_CA),
    /** A well known (publicly visible) identity */
    WELL_KNOWN_IDENTITY(3, NODE_CA),
    /** A confidential (limited visibility) identity */
    CONFIDENTIAL_IDENTITY(4, WELL_KNOWN_IDENTITY);

    companion object {
        fun getInstance(obj: ASN1Encodable): Role {
            val ordinal = (obj as ASN1Integer).positiveValue
            return Role.values().get(ordinal.toInt() - 1)
        }
    }

    override fun toASN1Primitive(): ASN1Primitive = ASN1Integer(asn1Val)
}