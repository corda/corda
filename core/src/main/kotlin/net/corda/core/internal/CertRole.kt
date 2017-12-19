package net.corda.core.internal

import net.corda.core.CordaOID
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import java.math.BigInteger
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
 * @property isWellKnown true if the role is a well known identity type (legal entity or service). This only makes sense
 * where [isIdentity] is true.
 */
// NOTE: The order of the entries in the enum MUST NOT be changed, as their ordinality is used as an identifier. Please
//       also note that IDs are numbered from 1 upwards, matching numbering of other enum types in ASN.1 specifications.
// TODO: Link to the specification once it has a permanent URL
enum class CertRole(val validParents: Set<CertRole?>, val isIdentity: Boolean, val isWellKnown: Boolean) : ASN1Encodable {
    /**
     * Intermediate CA (Doorman service).
     */
    INTERMEDIATE_CA(setOf(null), false, false),
    /** Signing certificate for the network map. */
    NETWORK_MAP(setOf(null), false, false),
    /** Well known (publicly visible) identity of a service (such as notary). */
    SERVICE_IDENTITY(setOf(INTERMEDIATE_CA), true, true),
    /** Node level CA from which the TLS and well known identity certificates are issued. */
    NODE_CA(setOf(INTERMEDIATE_CA), false, false),
    /** Transport layer security certificate for a node. */
    TLS(setOf(NODE_CA), false, false),
    /** Well known (publicly visible) identity of a legal entity. */
    LEGAL_IDENTITY(setOf(INTERMEDIATE_CA, NODE_CA), true, true),
    /** Confidential (limited visibility) identity of a legal entity. */
    CONFIDENTIAL_LEGAL_IDENTITY(setOf(LEGAL_IDENTITY), true, false);

    companion object {
        private var cachedRoles: Array<CertRole>? = null
        /**
         * Get a role from its ASN.1 encoded form.
         *
         * @throws IllegalArgumentException if the encoded data is not a valid role.
         */
        fun getInstance(id: ASN1Integer): CertRole {
            if (cachedRoles == null) {
                cachedRoles = CertRole.values()
            }
            val idVal = id.value
            require(idVal.compareTo(BigInteger.ZERO) > 0) { "Invalid role ID" }
            return try {
                val ordinal = idVal.intValueExact() - 1
                cachedRoles!![ordinal]
            } catch (ex: ArithmeticException) {
                throw IllegalArgumentException("Invalid role ID")
            } catch (ex: ArrayIndexOutOfBoundsException) {
                throw IllegalArgumentException("Invalid role ID")
            }
        }

        /**
         * Get a role from its ASN.1 encoded form.
         *
         * @throws IllegalArgumentException if the encoded data is not a valid role.
         */
        fun getInstance(data: ByteArray): CertRole = getInstance(ASN1Integer.getInstance(data))

        /**
         * Get a role from a certificate.
         *
         * @return the role if the extension is present, or null otherwise.
         * @throws IllegalArgumentException if the extension is present but is invalid.
         */
        fun extract(cert: Certificate): CertRole? {
            val x509Cert = cert as? X509Certificate
            return if (x509Cert != null) {
                extract(x509Cert)
            } else {
                null
            }
        }

        /**
         * Get a role from a certificate.
         *
         * @return the role if the extension is present, or null otherwise.
         * @throws IllegalArgumentException if the extension is present but is invalid.
         */
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