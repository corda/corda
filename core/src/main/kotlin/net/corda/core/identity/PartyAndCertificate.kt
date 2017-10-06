package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.security.cert.*

/**
 * A full party plus the X.509 certificate and path linking the party back to a trust root. Equality of
 * [PartyAndCertificate] instances is based on the party only, as certificate and path are data associated with the party,
 * not part of the identifier themselves.
 */
@CordaSerializable
class PartyAndCertificate(val certPath: CertPath) {
    @Transient
    val certificate: X509Certificate

    init {
        require(certPath.type == "X.509") { "Only X.509 certificates supported" }
        val certs = certPath.certificates
        require(certs.size >= 2) { "Certificate path must at least include subject and issuing certificates" }
        certificate = certs[0] as X509Certificate
    }

    @Transient
    val party: Party = Party(certificate)

    val owningKey: PublicKey get() = party.owningKey
    val name: CordaX500Name get() = party.name

    operator fun component1(): Party = party
    operator fun component2(): X509Certificate = certificate

    override fun equals(other: Any?): Boolean = other === this || other is PartyAndCertificate && other.party == party
    override fun hashCode(): Int = party.hashCode()
    override fun toString(): String = party.toString()

    /** Verify the certificate path is valid. */
    fun verify(trustAnchor: TrustAnchor): PKIXCertPathValidatorResult {
        val parameters = PKIXParameters(setOf(trustAnchor)).apply { isRevocationEnabled = false }
        val validator = CertPathValidator.getInstance("PKIX")
        return validator.validate(certPath, parameters) as PKIXCertPathValidatorResult
    }
}
