package net.corda.core.internal

import net.corda.core.CordaOID
import net.corda.core.utilities.NonEmptySet
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import java.math.BigInteger
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
enum class CertRole(val validParents: NonEmptySet<CertRole?>, val isIdentity: Boolean, val isWellKnown: Boolean) : ASN1Encodable {
    /** Intermediate CA (Doorman service). */
    INTERMEDIATE_CA(NonEmptySet.of(null), false, false),
    /** Signing certificate for the network map. */
    NETWORK_MAP(NonEmptySet.of(null), false, false),
    /** Well known (publicly visible) identity of a service (such as notary). */
    SERVICE_IDENTITY(NonEmptySet.of(INTERMEDIATE_CA), true, true),
    /** Node level CA from which the TLS and well known identity certificates are issued. */
    NODE_CA(NonEmptySet.of(INTERMEDIATE_CA), false, false),
    /** Transport layer security certificate for a node. */
    TLS(NonEmptySet.of(NODE_CA), false, false),
    /** Well known (publicly visible) identity of a legal entity. */
    // TODO: at the moment, Legal Identity certs are issued by Node CA only. However, [INTERMEDIATE_CA] is also added
    //      as a valid parent of [LEGAL_IDENTITY] for backwards compatibility purposes (eg. if we decide TLS has its
    //      own Root CA and Intermediate CA directly issues Legal Identities; thus, there won't be a requirement for
    //      Node CA). Consider removing [INTERMEDIATE_CA] from [validParents] when the model is finalised.
    LEGAL_IDENTITY(NonEmptySet.of(INTERMEDIATE_CA, NODE_CA), true, true),
    /** Confidential (limited visibility) identity of a legal entity. */
    CONFIDENTIAL_LEGAL_IDENTITY(NonEmptySet.of(LEGAL_IDENTITY), true, false);

    companion object {
        private val values by lazy(LazyThreadSafetyMode.NONE, CertRole::values)

        /**
         * Get a role from its ASN.1 encoded form.
         *
         * @throws IllegalArgumentException if the encoded data is not a valid role.
         */
        fun getInstance(id: ASN1Integer): CertRole {
            val idVal = id.value
            require(idVal > BigInteger.ZERO) { "Invalid role ID" }
            return try {
                val ordinal = idVal.intValueExact() - 1
                values[ordinal]
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
        fun extract(cert: X509Certificate): CertRole? {
            return cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE)?.let {
                val extensionString = DEROctetString.getInstance(it)
                getInstance(extensionString.octets)
            }
        }
    }

    fun isValidParent(parent: CertRole?): Boolean = parent in validParents

    override fun toASN1Primitive(): ASN1Primitive = ASN1Integer(this.ordinal + 1L)
}