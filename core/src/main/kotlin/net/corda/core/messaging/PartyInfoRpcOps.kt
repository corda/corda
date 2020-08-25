package net.corda.core.messaging

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import java.security.PublicKey

/** Operations that provide party information. */
interface PartyInfoRpcOps : RPCOps {

    /** Returns the [Party] corresponding to the given key, if found. */
    fun partyFromKey(key: PublicKey): Party?

    /** Returns the [Party] with the X.500 principal as it's [Party.name]. */
    fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party?

    /**
     * Returns a node's info from the network map cache, where known.
     * Notice that when there are more than one node for a given name (in case of distributed services) first service node
     * found will be returned.
     *
     * @return the node info if available.
     */
    fun nodeInfoFromParty(party: AbstractParty): NodeInfo?


    // TODO These need rethinking. Instead of these direct calls we should have a way of replicating a subset of
    // the node's state locally and query that directly.
    /**
     * Returns the well known identity from an abstract party. This is intended to resolve the well known identity
     * from a confidential identity, however it transparently handles returning the well known identity back if
     * a well known identity is passed in.
     *
     * @param party identity to determine well known identity for.
     * @return well known identity, if found.
     */
    fun wellKnownPartyFromAnonymous(party: AbstractParty): Party?

    /**
     * Get a notary identity by name.
     *
     * @return the notary identity, or null if there is no notary by that name. Note that this will return null if there
     * is a peer with that name but they are not a recognised notary service.
     */
    fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party?

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