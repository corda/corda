package net.corda.core.identity

import net.corda.core.crypto.subject
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * A pair of an anonymous party and the certificate path to prove it is owned by a well known identity. This class
 * does not validate the certificate path matches the party, and should not be trusted without being verified, for example
 * using [IdentityService.verifyAnonymousIdentity].
 *
 * Although similar to [VerifiedParty], the two distinct types exist in order to minimise risk of mixing up
 * confidential and well known identities. In contrast to [VerifiedParty] equality tests are based on the anonymous
 * party's key rather than name, which is the appropriate test for confidential parties but not for well known identities.
 */
@CordaSerializable
data class VerifiedAnonymousParty(
        val party: AnonymousParty,
        val certPath: CertPath) {
    constructor(party: PublicKey, certPath: CertPath)
            : this(AnonymousParty(party), certPath)
    init {
        require(certPath.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
    }

    /**
     * Get the X.500 name of the certificate for the anonymous party.
     *
     * @return the X.500 name if the anonymous party's certificate is an X.509 certificate, or null otherwise.
     */
    val name: X500Name?
        get() = (certPath.certificates.first() as? X509Certificate)?.subject

    override fun equals(other: Any?): Boolean {
        return other === this
                || (other is VerifiedAnonymousParty && party == other.party)
    }

    override fun hashCode(): Int = party.hashCode()
}