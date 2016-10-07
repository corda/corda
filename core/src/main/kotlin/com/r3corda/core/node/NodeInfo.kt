package com.r3corda.core.node

import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.node.services.ServiceType

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in protocols and is distinct from the Node's legalIdentity
 */
data class ServiceEntry(val info: ServiceInfo, val identity: Party)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
data class NodeInfo(val address: SingleMessageRecipient,
                    val legalIdentity: Party,
                    var advertisedServices: List<ServiceEntry> = emptyList(),
                    val physicalLocation: PhysicalLocation? = null) {
    val notaryIdentity: Party get() = advertisedServices.single { it.info.type.isNotary() }.identity
    fun serviceIdentities(type: ServiceType): List<Party> = advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity }
}
