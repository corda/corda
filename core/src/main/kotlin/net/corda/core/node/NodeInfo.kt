package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.CordaSerializable

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
@CordaSerializable
data class ServiceEntry(val info: ServiceInfo, val identity: PartyAndCertificate)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
// TODO The only support for multi-IP/multi-identity nodes required as part of this project is slots in the data structures.
//  Enhancing the node to support the rest of the feature is not a goal.
@CordaSerializable
data class NodeInfo(val addresses: List<SingleMessageRecipient>,
                    val legalIdentitiesAndCerts: Set<PartyAndCertificate>,
                    val platformVersion: Int,
                    var advertisedServices: List<ServiceEntry> = emptyList(),
                    val worldMapLocation: WorldMapLocation? = null) {
    init {
        require(advertisedServices.none { it.identity == legalIdentityAndCert }) { "Service identities must be different from node legal identity" }
    }

    val legalIdentityAndCert: PartyAndCertificate
        get() = legalIdentitiesAndCerts.first() // TODO different handling
    val legalIdentity: Party
        get() = legalIdentityAndCert.party
    val notaryIdentity: Party
        get() = advertisedServices.single { it.info.type.isNotary() }.identity.party
    fun serviceIdentities(type: ServiceType): List<Party> {
        return advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity.party }
    }
    fun servideIdentitiesAndCert(type: ServiceType): List<PartyAndCertificate> {
        return advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity }
    }
}
