package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
@CordaSerializable
data class ServiceEntry(val info: ServiceInfo, val identity: PartyAndCertificate)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
// TODO We currently don't support multi-IP/multi-identity nodes, we only left slots in the data structures.
//  Note that order of `legalIdentitiesAndCerts` is now important. We still treat the first identity as a special one.
//  It will change after introducing proper multi-identity management.
@CordaSerializable
data class NodeInfo(val addresses: List<NetworkHostAndPort>,
                    val legalIdentitiesAndCerts: List<PartyAndCertificate>,
                    val platformVersion: Int,
                    val advertisedServices: List<ServiceEntry> = emptyList(),
                    val serial: Long
) {
    init {
        require(legalIdentitiesAndCerts.isNotEmpty()) { "Node should have at least one legal identity" }
    }

    // TODO This part will be removed with services removal.
    val notaryIdentity: Party get() = advertisedServices.single { it.info.type.isNotary() }.identity.party
    fun serviceIdentities(type: ServiceType): List<Party> {
        return advertisedServices.mapNotNull { if (it.info.type.isSubTypeOf(type)) it.identity.party else null }
    }

    /**
     * Uses node's owner X500 name to infer the node's location. Used in Explorer in map view.
     */
    fun getWorldMapLocation(): WorldMapLocation? {
        val nodeOwnerLocation = legalIdentitiesAndCerts.first().name.locality
        return nodeOwnerLocation.let { CityDatabase[it] }
    }

    val legalIdentities: List<Party> get() = legalIdentitiesAndCerts.map { it.party }

    /** Returns true iff [party] is one of the legal identities of this node. */
    fun isLegalIdentity(party: Party): Boolean = legalIdentitiesAndCerts.any { it.party == party }
}
