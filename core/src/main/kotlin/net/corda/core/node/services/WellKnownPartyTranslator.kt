package net.corda.core.node.services

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party

interface WellKnownPartyTranslator {
    /**
     * Resolves a party name to the well known identity [Party] instance for this name. Where possible well known identity
     * lookup from name should be done from the network map (via [NetworkMapCache]) instead, as it is the authoritative
     * source of well known identities.
     *
     * @param name The [CordaX500Name] to determine well known identity for.
     * @return If known the canonical [Party] with that name, else null.
     */
    fun wellKnownPartyFromX500Name(name: CordaX500Name): Party?

    /**
     * Resolves a (optionally) confidential identity to the corresponding well known identity [Party].
     * It transparently handles returning the well known identity back if a well known identity is passed in.
     *
     * @param party identity to determine well known identity for.
     * @return well known identity, if found.
     */
    fun wellKnownPartyFromAnonymous(party: AbstractParty): Party?
}