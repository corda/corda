/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
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
interface NetworkMapCache : NetworkMapCacheBase {
    @CordaSerializable
    sealed class MapChange {
        abstract val node: NodeInfo

        data class Added(override val node: NodeInfo) : MapChange()
        data class Removed(override val node: NodeInfo) : MapChange()
        data class Modified(override val node: NodeInfo, val previousNode: NodeInfo) : MapChange()
    }

    /**
     * Look up the node info for a specific party. Will attempt to de-anonymise the party if applicable; if the party
     * is anonymised and the well known party cannot be resolved, it is impossible ot identify the node and therefore this
     * returns null.
     * Notice that when there are more than one node for a given party (in case of distributed services) first service node
     * found will be returned. See also: [NetworkMapCache.getNodesByLegalIdentityKey].
     *
     * @param party party to retrieve node information for.
     * @return the node for the identity, or null if the node could not be found. This does not necessarily mean there is
     * no node for the party, only that this cache is unaware of it.
     */
    fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo?
}

/** Subset of [NetworkMapCache] that doesn't depend on an [IdentityService]. */
@DoNotImplement
interface NetworkMapCacheBase {
    // DOCSTART 1
    /**
     * A list of notary services available on the network.
     *
     * Note that the identities are sorted based on legal name, and the ordering might change once new notaries are introduced.
     */
    val notaryIdentities: List<Party>
    // DOCEND 1

    /** Tracks changes to the network map cache. */
    val changed: Observable<NetworkMapCache.MapChange>
    /** Future to track completion of the NetworkMapService registration. */
    @get:DeleteForDJVM val nodeReady: CordaFuture<Void?>

    /**
     * Atomically get the current party nodes and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    fun track(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange>

    /**
     * Return a [NodeInfo] which has the given legal name for one of its identities, or null if no such node is found.
     *
     * @throws IllegalArgumentException If more than one matching node is found, in the case of a distributed service identity
     * (such as with a notary cluster). For such a scenerio use [getNodesByLegalName] instead.
     */
    fun getNodeByLegalName(name: CordaX500Name): NodeInfo?

    /**
     * Return a list of [NodeInfo]s which have the given legal name for one of their identities, or an empty list if no
     * such nodes are found.
     *
     * Normally there is at most one node for a legal name, but for distributed service identities (such as with a notary
     * cluster) there can be multiple nodes sharing the same identity.
     */
    fun getNodesByLegalName(name: CordaX500Name): List<NodeInfo>

    /** Look up the node info for a host and port. */
    fun getNodeByAddress(address: NetworkHostAndPort): NodeInfo?

    /**
     * Look up a well known identity (including certificate path) of a legal name. This should be used in preference
     * to well known identity lookup in the identity service where possible, as the network map is the authoritative
     * source of well known identities.
     */
    fun getPeerCertificateByLegalName(name: CordaX500Name): PartyAndCertificate?

    /**
     * Look up the well known identity of a legal name. This should be used in preference
     * to well known identity lookup in the identity service where possible, as the network map is the authoritative
     * source of well known identities.
     */
    fun getPeerByLegalName(name: CordaX500Name): Party? = getPeerCertificateByLegalName(name)?.party

    /** Return all [NodeInfo]s the node currently is aware of (including ourselves). */
    val allNodes: List<NodeInfo>

    /**
     * Look up the node information entries for a specific identity key.
     * Note that normally there will be only one node for a key, but for clusters of nodes or distributed services there
     * can be multiple nodes.
     */
    fun getNodesByLegalIdentityKey(identityKey: PublicKey): List<NodeInfo>

    /** Returns information about the party, which may be a specific node or a service */
    fun getPartyInfo(party: Party): PartyInfo?

    // DOCSTART 2
    /** Look up a well known identity of notary by legal name. */
    fun getNotary(name: CordaX500Name): Party? = notaryIdentities.firstOrNull { it.name == name }
    // DOCEND 2

    /** Returns true if and only if the given [Party] is a notary, which is defined by the network parameters. */
    fun isNotary(party: Party): Boolean = party in notaryIdentities

    /**
     * Returns true if and only if the given [Party] is validating notary. For every party that is a validating notary,
     * [isNotary] is also true.
     * @see isNotary
     */
    fun isValidatingNotary(party: Party): Boolean

    /** Clear all network map data from local node cache. */
    fun clearNetworkMapCache()
}
