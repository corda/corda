package net.corda.core.node.services

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.Party
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey

/**
 * An identity service maintains an bidirectional map of [Party]s to their associated public keys and thus supports
 * lookup of a party given its key. This is obviously very incomplete and does not reflect everything a real identity
 * service would provide.
 */
interface IdentityService {
    fun registerIdentity(party: Party)

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

    fun partyFromAnonymous(party: AnonymousParty): Party?
    fun partyFromAnonymous(partyRef: PartyAndReference) = partyFromAnonymous(partyRef.party)
}
