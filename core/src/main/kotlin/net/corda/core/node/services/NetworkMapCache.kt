package net.corda.core.node.services

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Contract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.randomOrNull
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.X500Name
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

    /** A list of all nodes the cache is aware of */
    val partyNodes: List<NodeInfo>
    /** A list of nodes that advertise a network map service */
    val networkMapNodes: List<NodeInfo>
    /** A list of nodes that advertise a notary service */
    val notaryNodes: List<NodeInfo> get() = getNodesWithService(ServiceType.notary)
    /**
     * A list of nodes that advertise a regulatory service. Identifying the correct regulator for a trade is outside
     * the scope of the network map service, and this is intended solely as a sanity check on configuration stored
     * elsewhere.
     */
    val regulatorNodes: List<NodeInfo> get() = getNodesWithService(ServiceType.regulator)
    /** Tracks changes to the network map cache */
    val changed: Observable<MapChange>
    /** Future to track completion of the NetworkMapService registration. */
    val mapServiceRegistered: ListenableFuture<Unit>

    /**
     * Atomically get the current party nodes and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    fun track(): Pair<List<NodeInfo>, Observable<MapChange>>

    /** Get the collection of nodes which advertise a specific service. */
    fun getNodesWithService(serviceType: ServiceType): List<NodeInfo> {
        return partyNodes.filter { it.advertisedServices.any { it.info.type.isSubTypeOf(serviceType) } }
    }

    /**
     * Get a recommended node that advertises a service, and is suitable for the specified contract and parties.
     * Implementations might understand, for example, the correct regulator to use for specific contracts/parties,
     * or the appropriate oracle for a contract.
     */
    fun getRecommended(type: ServiceType, contract: Contract, vararg party: Party): NodeInfo? = getNodesWithService(type).firstOrNull()

    /**
     * Look up the node info for a specific party. Will attempt to de-anonymise the party if applicable; if the party
     * is anonymised and the well known party cannot be resolved, it is impossible ot identify the node and therefore this
     * returns null.
     *
     * @param party party to retrieve node information for.
     * @return the node for the identity, or null if the node could not be found. This does not necessarily mean there is
     * no node for the party, only that this cache is unaware of it.
     */
    fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo?

    /** Look up the node info for a legal name. */
    fun getNodeByLegalName(principal: X500Name): NodeInfo? = partyNodes.singleOrNull { it.legalIdentity.name == principal }

    /**
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*.
     */

    /** Look up the node info for a specific peer key. */
    fun getNodeByLegalIdentityKey(identityKey: PublicKey): NodeInfo?

    /** Look up all nodes advertising the service owned by [publicKey] */
    fun getNodesByAdvertisedServiceIdentityKey(publicKey: PublicKey): List<NodeInfo> {
        return partyNodes.filter { it.advertisedServices.any { it.identity.owningKey == publicKey } }
    }

    /** Returns information about the party, which may be a specific node or a service */
    fun getPartyInfo(party: Party): PartyInfo?

    /** Gets a notary identity by the given name. */
    fun getNotary(principal: X500Name): Party? {
        val notaryNode = notaryNodes.randomOrNull {
            it.advertisedServices.any { it.info.type.isSubTypeOf(ServiceType.notary) && it.info.name == principal }
        }
        return notaryNode?.notaryIdentity
    }

    /**
     * Returns a notary identity advertised by any of the nodes on the network (chosen at random)
     * @param type Limits the result to notaries of the specified type (optional)
     */
    fun getAnyNotary(type: ServiceType? = null): Party? {
        val nodes = if (type == null) {
            notaryNodes
        } else {
            require(type != ServiceType.notary && type.isSubTypeOf(ServiceType.notary)) {
                "The provided type must be a specific notary sub-type"
            }
            notaryNodes.filter { it.advertisedServices.any { it.info.type == type } }
        }
        return nodes.randomOrNull()?.notaryIdentity
    }

    /** Checks whether a given party is an advertised notary identity */
    fun isNotary(party: Party): Boolean = notaryNodes.any { it.notaryIdentity == party }

    /** Checks whether a given party is an advertised validating notary identity */
    fun isValidatingNotary(party: Party): Boolean {
        val notary = notaryNodes.firstOrNull { it.notaryIdentity == party }
                ?: throw IllegalArgumentException("No notary found with identity $party. This is most likely caused " +
                "by using the notary node's legal identity instead of its advertised notary identity. " +
                "Your options are: ${notaryNodes.map { "\"${it.notaryIdentity.name}\"" }.joinToString()}.")
        return notary.advertisedServices.any { it.info.type.isValidatingNotary() }
    }
}
