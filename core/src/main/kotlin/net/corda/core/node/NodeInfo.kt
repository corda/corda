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
@CordaSerializable
data class NodeInfo(val addresses: List<NetworkHostAndPort>,
                    /** Non-empty list of all the identities, plus certificates, that belong to this node. */
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

    @Transient private var _legalIdentities: List<Party>? = null
    val legalIdentities: List<Party> get() {
        return _legalIdentities ?: legalIdentitiesAndCerts.map { it.party }.also { _legalIdentities = it }
    }

    /** Returns true if [party] is one of the identities of this node, else false. */
    fun isLegalIdentity(party: Party): Boolean = party in legalIdentities

    fun serviceIdentities(type: ServiceType): List<Party> {
        return advertisedServices.mapNotNull { if (it.info.type.isSubTypeOf(type)) it.identity.party else null }
    }
}
