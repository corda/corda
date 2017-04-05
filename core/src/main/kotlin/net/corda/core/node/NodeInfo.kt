package net.corda.core.node

import net.corda.core.crypto.Party
import net.corda.core.flows.AdvertisedFlow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.CordaSerializable

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
@CordaSerializable
data class ServiceEntry(val info: ServiceInfo, val identity: Party)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
@CordaSerializable
data class NodeInfo(val address: SingleMessageRecipient,
                    val legalIdentity: Party,
                    val version: Version,
                    var advertisedServices: List<ServiceEntry> = emptyList(),
                    // Flows advertised by this node as peer flows, opposed to service flows.
                    var advertisedPeerFlows: List<AdvertisedFlow> = emptyList(),
                    val physicalLocation: PhysicalLocation? = null) {
    init {
        require(advertisedServices.none { it.identity == legalIdentity }) { "Service identities must be different from node legal identity" }
        require((advertisedServices.distinctBy { it.identity.owningKey }).size == advertisedServices.size) { "All services should have different owning keys" }
        // TODO Sanity check that we don't have more advertised flows on the same name. Overload plus on AdvertisedServices.
        require(advertisedPeerFlows.groupBy { it.genericFlowName }.size == advertisedPeerFlows.size)
    }

    val notaryIdentity: Party get() = advertisedServices.single { it.info.type.isNotary() }.identity
    fun serviceIdentities(type: ServiceType): List<Party> = advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity }
}
