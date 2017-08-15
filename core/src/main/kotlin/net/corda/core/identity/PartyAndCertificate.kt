package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.*
import java.util.*

/**
 * A full party plus the X.509 certificate and path linking the party back to a trust root. Equality of
 * [PartyAndCertificate] instances is based on the party only, as certificate and path are data associated with the party,
 * not part of the identifier themselves. While party and certificate can both be derived from the certificate path,
 * this class exists in order to ensure the implementation classes of certificates and party public keys are kept stable.
 */
@CordaSerializable
data class PartyAndCertificate(val party: Party,
                               val certificate: X509CertificateHolder,
                               val certPath: CertPath) {
    constructor(name: X500Name, owningKey: PublicKey, certificate: X509CertificateHolder, certPath: CertPath) : this(Party(name, owningKey), certificate, certPath)
    val name: X500Name
        get() = party.name
    val owningKey: PublicKey
        get() = party.owningKey

    override fun equals(other: Any?): Boolean {
        return if (other is PartyAndCertificate)
            party == other.party
        else
            false
    }

    override fun hashCode(): Int = party.hashCode()
    override fun toString(): String = party.toString()

    /**
     * Verify that the given certificate path is valid and leads to the owning key of the party.
     */
    fun verify(trustAnchor: TrustAnchor): PKIXCertPathValidatorResult {
        require(certPath.certificates.first() is X509Certificate) { "Subject certificate must be an X.509 certificate" }
        require(Arrays.equals(party.owningKey.encoded, certificate.subjectPublicKeyInfo.encoded)) { "Certificate public key must match party owning key" }
        require(Arrays.equals(certPath.certificates.first().encoded, certificate.encoded)) { "Certificate path must link to certificate" }

        val validatorParameters = PKIXParameters(setOf(trustAnchor))
        val validator = CertPathValidator.getInstance("PKIX")
        validatorParameters.isRevocationEnabled = false
        return validator.validate(certPath, validatorParameters) as PKIXCertPathValidatorResult
    }
}
