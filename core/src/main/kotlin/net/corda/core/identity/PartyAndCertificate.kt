package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
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
data class PartyAndCertificate<P : AbstractParty>(val party: P,
                                                  val certificate: X509CertificateHolder,
                                                  val certPath: CertPath) {
    companion object {
        fun build(name: X500Name, owningKey: PublicKey, certificate: X509CertificateHolder, certPath: CertPath): PartyAndCertificate<Party> {
            return PartyAndCertificate(Party(name, owningKey), certificate, certPath)
        }

        fun build(owningKey: PublicKey, certificate: X509CertificateHolder, certPath: CertPath): PartyAndCertificate<AnonymousParty> {
            return PartyAndCertificate(AnonymousParty(owningKey), certificate, certPath)
        }
    }
    val owningKey: PublicKey
        get() = party.owningKey

    override fun equals(other: Any?): Boolean {
        return if (other is PartyAndCertificate<*>)
            party == other.party
        else
            false
    }

    override fun hashCode(): Int = party.hashCode()
    override fun toString(): String = party.toString()
}
