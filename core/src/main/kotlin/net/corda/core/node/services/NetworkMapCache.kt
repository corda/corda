package net.corda.core.node.services

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Contract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.randomOrNull
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort
import rx.Observable
import java.security.PublicKey

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. The cache wraps around a map fetched
 * from an authoritative service, and adds easy lookup of the data stored within it. Generally it would be initialised
 * with a specified network map service, which it fetches data from and then subscribes to updates of.
 */
interface NetworkMapCache {

    @CordaSerializable
    sealed class MapChange {
        abstract val node: NodeInfo

        data class Added(override val node: NodeInfo) : MapChange()
        data class Removed(override val node: NodeInfo) : MapChange()
        data class Modified(override val node: NodeInfo, val previousNode: NodeInfo) : MapChange()
    }

    // TODO get rid of party nodes
    /** A list of all nodes the cache is aware of */
    val partyNodes: List<NodeInfo> //todo move it to persistent
    //Remove the concept of network services. Update the DemoBench tool and cash app to fix issue #567 and work through any other impact.
    //As a temporary hack, just assume for now that every network has a notary service named "Notary Service" that can be looked up in the map.
    //This should eliminate the only required usage of services.
    /** A list of parties that run as a notary service */
    // TODO this list will be taken from NetworkParameters distributed by NetworkMap.
    val notaryIdentities: List<PartyAndCertificate> get() = partyNodes.filter { it.legalIdentitiesAndCerts.any { it.name.toString().contains("notary", true) }}.map { it.legalIdentitiesAndCerts[1] }
    /** Tracks changes to the network map cache */
    val changed: Observable<MapChange>
    /** Future to track completion of the NetworkMapService registration. */
    val nodeReady: CordaFuture<Void?>

    /**
     * Atomically get the current party nodes and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    fun track(): DataFeed<List<NodeInfo>, MapChange>

    /**
     * Look up the node info for a specific party. Will attempt to de-anonymise the party if applicable; if the party
     * is anonymised and the well known party cannot be resolved, it is impossible ot identify the node and therefore this
     * returns null.
     *
     * @param party party to retrieve node information for.
     * @return the node for the identity, or null if the node could not be found. This does not necessarily mean there is
     * no node for the party, only that this cache is unaware of it.
     */
    // TODO remove that from API
    fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo?

    /** Look up the node info for a legal name. */
    fun getNodeByLegalName(principal: CordaX500Name): NodeInfo?

    /** Look up the node info for a host and port. */
    fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo?

    fun getPeerByLegalName(principal: CordaX500Name): Party? = getNodeByLegalName(principal)?.let {
        it.legalIdentitiesAndCerts.singleOrNull { it.name == principal }?.party
    }

    /**
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*.
     */
    /** Look up the node infos for a specific peer key. */
    fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo>

    /** Returns information about the party, which may be a specific node or a service */
    fun getPartyInfo(party: Party): PartyInfo?

    /** Gets a notary identity by the given name. */
    fun getNotary(principal: CordaX500Name): Party? = notaryIdentities.filter { it.name == principal }.randomOrNull()?.party

    /**
     * Returns a notary identity advertised by any of the nodes on the network (chosen at random)
     * @param type Limits the result to notaries of the specified type (optional)
     */
    fun getAnyNotary(): Party? = notaryIdentities.randomOrNull()?.party

    /** Checks whether a given party is an advertised notary identity */
    fun isNotary(party: Party): Boolean = party in notaryIdentities.map { it.party }

    /**
     * Clear all network map data from local node cache.
     */
    fun clearNetworkMapCache()
}
