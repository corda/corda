package net.corda.core.node.services

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Contract
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.AdvertisedFlow
import net.corda.core.messaging.MessagingService
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.randomOrNull
import net.corda.core.serialization.CordaSerializable
import rx.Observable
import java.util.*

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. The cache wraps around a map fetched
 * from an authoritative service, and adds easy lookup of the data stored within it. Generally it would be initialised
 * with a specified network map service, which it fetches data from and then subscribes to updates of.
 */
interface NetworkMapCache {

    @CordaSerializable
    sealed class MapChange(val node: NodeInfo) {
        class Added(node: NodeInfo) : MapChange(node)
        class Removed(node: NodeInfo) : MapChange(node)
        class Modified(node: NodeInfo, val previousNode: NodeInfo) : MapChange(node)
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

    /** Look up the node info for a legal name. */
    fun getNodeByLegalName(name: String): NodeInfo? = partyNodes.singleOrNull { it.legalIdentity.name == name }

    /**
     * In general, nodes can advertise multiple identities: a legal identity, and separate identities for each of
     * the services it provides. In case of a distributed service – run by multiple nodes – each participant advertises
     * the identity of the *whole group*.
     */

    /** Look up the node info for a specific peer key. */
    fun getNodeByLegalIdentityKey(compositeKey: CompositeKey): NodeInfo?

    /** Look up all nodes advertising the service owned by [compositeKey] */
    fun getNodesByAdvertisedServiceIdentityKey(compositeKey: CompositeKey): List<NodeInfo> {
        return partyNodes.filter { it.advertisedServices.any { it.identity.owningKey == compositeKey } }
    }

    // TODO It shouldn't be checked in NMC.
    // TODO It needs more thinking, how to handle situations when we have distributed service, but nodes run different versions.
    /**
     * Look up all flow versions from nodes advertising the service owned by [compositeKey]. Return AdvertisedFlow.
     * If nodes advertise different flow versions (in theory they shouldn't) throw exception.
     */
    //  It's version where we have grouped flows advertised by the service.
//    private fun getServiceFlowVersions(compositeKey: CompositeKey, flowName: String): AdvertisedFlow? {
//        val maybeFlowsInfo = ArrayList<AdvertisedFlow?>()
//        for (node in partyNodes) {
//            // Assuming that node doesn't advertise two services with the same key.
//            val match = node.advertisedServices.find { it.identity.owningKey == compositeKey }
//            if (match != null) {
//                val flow = match.info.advertisedFlows.filter { it.genericFlowName == flowName }
//                // TODO What if one node doesn't advertise anything at all and the rest does.
//                if (flow.size == 1)
//                    maybeFlowsInfo.add(flow[0])
//                else if (flow.size > 1) throw IllegalArgumentException("Service advertises more than one flow entry for the same flow.")
//            }
//        }
//        if (maybeFlowsInfo.isEmpty()) return null
//        else {
//            val firstVs = maybeFlowsInfo[0]
//            require (maybeFlowsInfo.all { it == firstVs }) { "Distributed service nodes should advertise the same flow versions." } //TODO That check should be done on service registration to NMS.
//            return firstVs // Can also return null. It will mean that none of service nodes advertises this flow.
//        }
//    }

    // TODO Simpler function assuming that only peer flows are advertised.
    private fun getServiceFlowVersions(compositeKey: CompositeKey, flowName: String): AdvertisedFlow? {
        val maybeFlowsInfo = ArrayList<AdvertisedFlow?>()
        for (node in partyNodes) {
            // Assuming that node doesn't advertise two services with the same key.
            val match = node.advertisedServices.any { it.identity.owningKey == compositeKey }
            if (match) {
                val flow = node.advertisedPeerFlows.filter { it.genericFlowName == flowName }
                if (flow.size == 1)
                    maybeFlowsInfo.add(flow[0])
                else if (flow.size > 1) throw IllegalArgumentException("Service advertises more than one flow entry for the same flow.")
            }
        }
        if (maybeFlowsInfo.isEmpty()) return null
        else {
            val firstVs = maybeFlowsInfo[0]
            require (maybeFlowsInfo.all { it == firstVs }) { "Distributed service nodes should advertise the same flow versions." }
            return firstVs // Can also return null. It will mean that none of service nodes advertises this flow.
        }
    }

    /**
     * Look up both nodes advertising service and peer nodes to figure out what flow versions are they speaking.
     * Takes key and flow we are looking for.
     */
    fun getFlowVersionInfo(compositeKey: CompositeKey, flowName: String): AdvertisedFlow? {
        val maybePeerInfo = getNodeByLegalIdentityKey(compositeKey)
        return if (maybePeerInfo != null) { // Assuming that node's key and services' keys are distinct.
            val flow = maybePeerInfo.advertisedPeerFlows.filter { it.genericFlowName == flowName }
            if (flow.isEmpty()) null
            else if (flow.size == 1) flow[0]
            else throw IllegalArgumentException("Peer advertises more than one flow entry for the same flow.")
        } else getServiceFlowVersions(compositeKey, flowName)
    }

    /** Returns information about the party, which may be a specific node or a service */
    fun getPartyInfo(party: Party): PartyInfo?

    /** Gets a notary identity by the given name. */
    fun getNotary(name: String): Party? {
        val notaryNode = notaryNodes.randomOrNull {
            it.advertisedServices.any { it.info.type.isSubTypeOf(ServiceType.notary) && it.info.name == name }
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

    /**
     * Add a network map service; fetches a copy of the latest map from the service and subscribes to any further
     * updates.
     * @param net the network messaging service.
     * @param networkMapAddress the network map service to fetch current state from.
     * @param subscribe if the cache should subscribe to updates.
     * @param ifChangedSinceVer an optional version number to limit updating the map based on. If the latest map
     * version is less than or equal to the given version, no update is fetched.
     */
    fun addMapService(net: MessagingService, networkMapAddress: SingleMessageRecipient,
                      subscribe: Boolean, ifChangedSinceVer: Int? = null): ListenableFuture<Unit>

    /** Adds a node to the local cache (generally only used for adding ourselves). */
    fun addNode(node: NodeInfo)

    /** Removes a node from the local cache. */
    fun removeNode(node: NodeInfo)

    /**
     * Deregister from updates from the given map service.
     * @param net the network messaging service.
     * @param service the network map service to fetch current state from.
     */
    fun deregisterForUpdates(net: MessagingService, service: NodeInfo): ListenableFuture<Unit>

    /** For testing where the network map cache is manipulated marks the service as immediately ready. */
    @VisibleForTesting
    fun runWithoutMapService()
}

@CordaSerializable
sealed class NetworkCacheError : Exception() {
    /** Indicates a failure to deregister, because of a rejected request from the remote node */
    class DeregistrationFailed : NetworkCacheError()
}
