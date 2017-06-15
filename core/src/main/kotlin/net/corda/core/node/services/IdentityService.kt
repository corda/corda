package net.corda.core.node.services

import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.*
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
    val trustRoot: X509Certificate?
    val trustRootHolder: X509CertificateHolder?

    /**
     * Verify and then store a well known identity.
     *
     * @param party a party representing a legal entity.
     * @throws IllegalArgumentException if the certificate path is invalid, or if there is already an existing
     * certificate chain for the anonymous party.
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun registerIdentity(party: PartyAndCertificate)

    /**
     * Verify and then store an identity.
     *
     * @param anonymousParty a party representing a legal entity in a transaction.
     * @param path certificate path from the trusted root to the party.
     * @throws IllegalArgumentException if the certificate path is invalid, or if there is already an existing
     * certificate chain for the anonymous party.
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun registerAnonymousIdentity(anonymousParty: AnonymousParty, party: Party, path: CertPath)

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
     * Get the certificate and path for a well known identity.
     *
     * @return the party and certificate, or null if unknown.
     */
    fun certificateFromParty(party: Party): PartyAndCertificate?

    // There is no method for removing identities, as once we are made aware of a Party we want to keep track of them
    // indefinitely. It may be that in the long term we need to drop or archive very old Party information for space,
    // but for now this is not supported.

    fun partyFromKey(key: PublicKey): Party?
    @Deprecated("Use partyFromX500Name or partiesFromName")
    fun partyFromName(name: String): Party?
    fun partyFromX500Name(principal: X500Name): Party?

    /**
     * Resolve the well known identity of a party. If the party passed in is already a well known identity
     * (i.e. a [Party]) this returns it as-is.
     *
     * @return the well known identity, or null if unknown.
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
     * Get the certificate chain showing an anonymous party is owned by the given party.
     */
    fun pathForAnonymous(anonymousParty: AnonymousParty): CertPath?

    /**
     * Returns a list of candidate matches for a given string, with optional fuzzy(ish) matching. Fuzzy matching may
     * get smarter with time e.g. to correct spelling errors, so you should not hard-code indexes into the results
     * but rather show them via a user interface and let the user pick the one they wanted.
     *
     * @param query The string to check against the X.500 name components
     * @param exactMatch If true, a case sensitive match is done against each component of each X.500 name.
     */
    fun partiesFromName(query: String, exactMatch: Boolean): Set<Party>

    class UnknownAnonymousPartyException(msg: String) : Exception(msg)
}
