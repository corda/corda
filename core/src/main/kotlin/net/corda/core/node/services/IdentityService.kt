package net.corda.core.node.services

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * An identity service maintains an bidirectional map of [Party]s to their associated public keys and thus supports
 * lookup of a party given its key. This is obviously very incomplete and does not reflect everything a real identity
 * service would provide.
 */
interface IdentityService {
    fun registerIdentity(party: Party)

    /**
     * Verify and then store the certificates proving that an anonymous party's key is owned by the given full
     * party.
     *
     * @param trustedRoot trusted root certificate, typically the R3 master signing certificate.
     * @param anonymousParty an anonymised party belonging to the legal entity.
     * @param path certificate path from the trusted root to the anonymised party.
     * @throws IllegalArgumentException if the chain does not link the two parties, or if there is already an existing
     * certificate chain for the anonymous party. Anonymous parties must always resolve to a single owning party.
     */
    // TODO: Move this into internal identity service once available
    @Throws(IllegalArgumentException::class)
    fun registerPath(trustedRoot: X509Certificate, anonymousParty: AnonymousParty, path: CertPath)

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
    fun getAllIdentities(): Iterable<Party>

    // There is no method for removing identities, as once we are made aware of a Party we want to keep track of them
    // indefinitely. It may be that in the long term we need to drop or archive very old Party information for space,
    // but for now this is not supported.

    fun partyFromKey(key: PublicKey): Party?
    @Deprecated("Use partyFromX500Name")
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

    class UnknownAnonymousPartyException(msg: String) : Exception(msg)
}
