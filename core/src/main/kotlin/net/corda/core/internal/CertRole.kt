package net.corda.core.internal

import net.corda.core.CordaOID
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Describes the Corda role a certificate is used for. This is used both to verify the hierarchy of certificates is
 * correct, and to determine which is the well known identity's certificate.
 *
 * @property validParents the parent role of this role - must match exactly for the certificate hierarchy to be valid for use
 * in Corda. Null indicates the parent certificate must have no role (the extension must be absent).
 * @property isIdentity true if the role is valid for use as a [net.corda.core.identity.Party] identity, false otherwise (the role is Corda
 * infrastructure of some kind).
 */
// NOTE: The order of the entries in the enum MUST NOT be changed, as their ordinality is used as an identifier. Please
//       also note that IDs are numbered from 1 upwards, matching numbering of other enum types in ASN.1 specifications.
// TODO: Link to the specification once it has a permanent URL
enum class CertRole(val validParents: Set<CertRole?>, val isIdentity: Boolean) : ASN1Encodable {
    /**
     * A Doorman (intermediate CA of some kind).
     */
    INTERMEDIATE_CA(setOf(null), false),
    /** Signing key for the network map */
    NETWORK_MAP(setOf(null), false),
    /** The node level CA from which the TLS and well known identity certificates are issued. */
    NODE_CA(setOf(INTERMEDIATE_CA), false),
    /** The transport layer security certificate */
    TLS(setOf(NODE_CA), false),
    /** A well known (publicly visible) identity of a service */
    // TODO: Lock this down to INTERMEDIATE_CA only
    SERVICE_IDENTITY(setOf(INTERMEDIATE_CA, NODE_CA), true),
    /** A well known (publicly visible) identity of a legal entity */
    WELL_KNOWN_IDENTITY(setOf(INTERMEDIATE_CA, NODE_CA), true),
    /** A confidential (limited visibility) identity */
    CONFIDENTIAL_IDENTITY(setOf(WELL_KNOWN_IDENTITY), true);

    companion object {
        fun getInstance(id: ASN1Integer): CertRole {
            val ordinal = id.positiveValue.toInt() - 1
            return CertRole.values().get(ordinal)
        }
        fun getInstance(data: ByteArray): CertRole? = getInstance(ASN1Integer.getInstance(data))

        fun extract(cert: Certificate): CertRole? {
            val x509Cert = cert as? X509Certificate
            return if (x509Cert != null) {
                extract(x509Cert)
            } else {
                null
            }
        }

        fun extract(cert: X509Certificate): CertRole? {
            val extensionData: ByteArray? = cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE)
            return if (extensionData != null) {
                val extensionString = DEROctetString.getInstance(extensionData)
                getInstance(extensionString.octets)
            } else {
                null
            }
        }
    }

    fun isValidParent(parent: CertRole?): Boolean = parent in validParents

    override fun toASN1Primitive(): ASN1Primitive = ASN1Integer(this.ordinal + 1L)
}