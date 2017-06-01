package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath

/**
 * A party plus the X.509 certificate and path linking the party back to a trust root.
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

    override fun toString(): String = party.toString()
}
