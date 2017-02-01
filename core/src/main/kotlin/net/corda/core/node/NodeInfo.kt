package net.corda.core.node

import net.corda.core.crypto.Party
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
data class ServiceEntry(val info: ServiceInfo, val identity: Party.Full)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
data class NodeInfo(val address: SingleMessageRecipient,
                    val legalIdentity: Party.Full,
                    var advertisedServices: List<ServiceEntry> = emptyList(),
                    val physicalLocation: PhysicalLocation? = null) {
    init {
        require(advertisedServices.none { it.identity == legalIdentity }) { "Service identities must be different from node legal identity" }
    }

    val notaryIdentity: Party.Full get() = advertisedServices.single { it.info.type.isNotary() }.identity
    fun serviceIdentities(type: ServiceType): List<Party.Full> = advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity }
}
