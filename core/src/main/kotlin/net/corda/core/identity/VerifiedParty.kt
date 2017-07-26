package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * A well known party plus the X.509 certificate and path linking the party back to a trust root. Equality of
 * [VerifiedParty] instances is based on the party only, as certificate and path are data associated with the party,
 * not part of the identifier themselves.
 *
 * This does not (yet) verify the certificate path, please use [InMemoryIdentityService.registerIdentity] to
 * verify and register these parties.
 */
@CordaSerializable
data class VerifiedParty(val party: Party,
                         val certificate: X509CertificateHolder,
                         val certPath: CertPath) {
    constructor(name: X500Name, owningKey: PublicKey, certificate: X509CertificateHolder, certPath: CertPath) : this(Party(name, owningKey), certificate, certPath)

    init {
        require(certPath.certificates.isNotEmpty()) { "Certificate path cannot be empty" }
        val targetCert = certPath.certificates.first() as X509Certificate
        val subjectX500Name = X500Name(targetCert.subjectDN.name)
        require(subjectX500Name == certificate.subject) { "Certificate path must end with the subject ${certificate.subject}" }
    }

    val name: X500Name
        get() = party.name
    val owningKey: PublicKey
        get() = party.owningKey

    override fun equals(other: Any?): Boolean {
        return if (other is VerifiedParty)
            party == other.party
        else
            false
    }

    override fun hashCode(): Int = party.hashCode()
    /**
     * Convert this party and certificate into an anomymised identity. This exists primarily for example cases which
     * want to use well known identities as if they're anonymous identities.
     */
    fun toAnonymisedIdentity(): VerifiedAnonymousParty {
        return VerifiedAnonymousParty(party.owningKey, certPath)
    }
    override fun toString(): String = party.toString()
}
