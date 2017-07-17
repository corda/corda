package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import net.corda.flows.AnonymisedIdentity
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath

/**
 * A full party plus the X.509 certificate and path linking the party back to a trust root. Equality of
 * [PartyAndCertificate] instances is based on the party only, as certificate and path are data associated with the party,
 * not part of the identifier themselves.
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
    /**
     * Convert this party and certificate into an anomymised identity. This exists primarily for example cases which
     * want to use well known identities as if they're anonymous identities.
     */
    fun toAnonymisedIdentity(): AnonymisedIdentity {
        return AnonymisedIdentity(certPath, certificate, party.owningKey)
    }
    override fun toString(): String = party.toString()
}
