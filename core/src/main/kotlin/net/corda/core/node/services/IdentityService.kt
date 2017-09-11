package net.corda.core.node.services

import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*

/**
 * An identity service maintains a directory of parties by their associated distinguished name/public keys and thus
 * supports lookup of a party given its key, or name. The service also manages the certificates linking confidential
 * identities back to the well known identity (i.e. the identity in the network map) of a party.
 */
interface IdentityService {
    val trustRoot: X509Certificate
    val trustRootHolder: X509CertificateHolder
    val trustAnchor: TrustAnchor
    val caCertStore: CertStore

    /**
     * Verify and then store an identity.
     *
     * @param identity a party and the certificate path linking them to the network trust root.
     * @return the issuing entity, if known.
     * @throws IllegalArgumentException if the certificate path is invalid.
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate?

    /**
     * Asserts that an anonymous party maps to the given full party, by looking up the certificate chain associated with
     * the anonymous party and resolving it back to the given full party.
     *
     * @throws IllegalStateException if the anonymous party is not owned by the full party.
     */
    @Throws(IllegalStateException::class)
    fun assertOwnership(party: Party, anonymousParty: AnonymousParty)

    /**
     * Get all identities known to the service. This is expensive, and [partyFromKey] or [partyFromX500Name] should be
     * used in preference where possible.
     */
    fun getAllIdentities(): Iterable<PartyAndCertificate>

    /**
     * Get the certificate and path for a well known identity's owning key.
     *
     * @return the party and certificate, or null if unknown.
     */
    fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate?

    /**
     * Get the certificate and path for a well known identity.
     *
     * @return the party and certificate.
     * @throws IllegalArgumentException if the certificate and path are unknown. This should never happen for a well
     * known identity.
     */
    fun certificateFromParty(party: Party): PartyAndCertificate

    // There is no method for removing identities, as once we are made aware of a Party we want to keep track of them
    // indefinitely. It may be that in the long term we need to drop or archive very old Party information for space,
    // but for now this is not supported.

    fun partyFromKey(key: PublicKey): Party?

    fun partyFromX500Name(principal: X500Name): Party?

    /**
     * Returns the well known identity from an abstract party. This is intended to resolve the well known identity
     * from a confidential identity, however it transparently handles returning the well known identity back if
     * a well known identity is passed in.
     *
     * @param party identity to determine well known identity for.
     * @return well known identity, if found.
     */
    fun partyFromAnonymous(party: AbstractParty): Party?

    /**
     * Resolve the well known identity of a party. If the party passed in is already a well known identity
     * (i.e. a [Party]) this returns it as-is.
     *
     * @return the well known identity, or null if unknown.
     */
    fun partyFromAnonymous(partyRef: PartyAndReference) = partyFromAnonymous(partyRef.party)

    /**
     * Resolve the well known identity of a party. Throws an exception if the party cannot be identified.
     * If the party passed in is already a well known identity (i.e. a [Party]) this returns it as-is.
     *
     * @return the well known identity.
     * @throws IllegalArgumentException
     */
    fun requirePartyFromAnonymous(party: AbstractParty): Party

    /**
     * Returns a list of candidate matches for a given string, with optional fuzzy(ish) matching. Fuzzy matching may
     * get smarter with time e.g. to correct spelling errors, so you should not hard-code indexes into the results
     * but rather show them via a user interface and let the user pick the one they wanted.
     *
     * @param query The string to check against the X.500 name components
     * @param exactMatch If true, a case sensitive match is done against each component of each X.500 name.
     */
    fun partiesFromName(query: String, exactMatch: Boolean): Set<Party>
}

class UnknownAnonymousPartyException(msg: String) : Exception(msg)
