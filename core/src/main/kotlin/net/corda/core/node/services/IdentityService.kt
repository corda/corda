package net.corda.core.node.services

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party

/**
 * An identity service maintains an bidirectional map of [Party.Full]s to their associated public keys and thus supports
 * lookup of a party given its key. This is obviously very incomplete and does not reflect everything a real identity
 * service would provide.
 */
interface IdentityService {
    fun registerIdentity(party: Party.Full)
    /**
     * Get all identities known to the service. This is expensive, and [partyFromKey] or [partyFromName] should be
     * used in preference where possible.
     */
    fun getAllIdentities(): Iterable<Party.Full>

    // There is no method for removing identities, as once we are made aware of a Party we want to keep track of them
    // indefinitely. It may be that in the long term we need to drop or archive very old Party information for space,
    // but for now this is not supported.

    fun partyFromKey(key: CompositeKey): Party.Full?
    fun partyFromName(name: String): Party.Full?

    fun deanonymiseParty(party: Party.Anonymised) = partyFromKey(party.owningKey)
    fun deanonymiseParty(partyAndRef: PartyAndReference) = partyFromKey(partyAndRef.party.owningKey)
}
