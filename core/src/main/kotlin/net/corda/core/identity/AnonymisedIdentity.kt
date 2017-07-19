package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath

@CordaSerializable
data class AnonymisedIdentity(
        val party: AnonymousParty,
        val certificate: X509CertificateHolder,
        val certPath: CertPath) {
    constructor(party: PublicKey, certificate: X509CertificateHolder, certPath: CertPath)
            : this(AnonymousParty(party), certificate, certPath)

    override fun equals(other: Any?): Boolean {
        return if (other is AnonymisedIdentity)
            party == other.party
        else
            false
    }

    override fun hashCode(): Int = party.hashCode()
}