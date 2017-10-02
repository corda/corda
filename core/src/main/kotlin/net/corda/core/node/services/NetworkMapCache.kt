package net.corda.core.node.services

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
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

    // DOCSTART 1
    /**
     * A list of notary services available on the network.
     *
     * Note that the identities are sorted based on legal name, and the ordering might change once new notaries are introduced.
     */
    // TODO this list will be taken from NetworkParameters distributed by NetworkMap.
    val notaryIdentities: List<Party>
    // DOCEND 1

    /** Tracks changes to the network map cache. */
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
     * Notice that when there are more than one node for a given party (in case of distributed services) first service node
     * found will be returned. See also: [getNodesByLegalIdentityKey].
     *
     * @param party party to retrieve node information for.
     * @return the node for the identity, or null if the node could not be found. This does not necessarily mean there is
     * no node for the party, only that this cache is unaware of it.
     */
    fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo?

    /**
     * Look up the node info for a legal name.
     * Notice that when there are more than one node for a given name (in case of distributed services) first service node
     * found will be returned.
     */
    fun getNodeByLegalName(name: CordaX500Name): NodeInfo?

    /** Look up the node info for a host and port. */
    fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo?

    /**
     * Look up all well known identities (including certificate path) of a legal name.
     */
    fun getPeerCertificatesByLegalName(name: CordaX500Name): Set<PartyAndCertificate> {
        return getNodesByLegalName(name)
                .flatMap(NodeInfo::legalIdentitiesAndCerts)
                .filter { it.name == name }
                .toSet()
    }

    /**
     * Look up a well known identity (including certificate path) of a legal name.
     */
    fun getPeerCertificateByLegalName(name: CordaX500Name): PartyAndCertificate? {
        return getNodeByLegalName(name)
                ?.legalIdentitiesAndCerts
                ?.singleOrNull { it.name == name }
    }

    /**
     * Look up all well known identities for a legal name.
     */
    fun getPeersByLegalName(name: CordaX500Name): Set<Party> {
        return getNodesByLegalName(name)
                .flatMap(NodeInfo::legalIdentitiesAndCerts)
                .filter { it.name == name }
                .map(PartyAndCertificate::party)
                .toSet()
    }

    /**
     * Look up the well known identity of a legal name.
     */
    fun getPeerByLegalName(name: CordaX500Name): Party? = getPeerCertificateByLegalName(name)?.party

    /** Return all [NodeInfo]s the node currently is aware of (including ourselves). */
    val allNodes: List<NodeInfo>

    /**
     * Look up the node information entries for a specific identity key.
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*.
     */
    fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo>

    /**
     * Look up the node information entries for a legal name.
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*.
     */
    fun getNodesByLegalName(name: CordaX500Name): List<NodeInfo>

    /** Returns information about the party, which may be a specific node or a service */
    fun getPartyInfo(party: Party): PartyInfo?

    // DOCSTART 2
    /** Gets a notary identity by the given name. */
    fun getNotary(name: CordaX500Name): Party? = notaryIdentities.firstOrNull { it.name == name }
    // DOCEND 2

    /** Checks whether a given party is an advertised notary identity. */
    fun isNotary(party: Party): Boolean = party in notaryIdentities

    /** Checks whether a given party is an validating notary identity. */
    fun isValidatingNotary(party: Party): Boolean {
        require(isNotary(party)) { "No notary found with identity $party." }
        return !party.name.toString().contains("corda.notary.simple", true) // TODO This implementation will change after introducing of NetworkParameters.
    }

    /** Clear all network map data from local node cache. */
    fun clearNetworkMapCache()
}
