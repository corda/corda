package core.node

import core.Party
import core.messaging.SingleMessageRecipient
import core.node.services.ServiceType

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
data class NodeInfo(val address: SingleMessageRecipient, val identity: Party,
                    var advertisedServices: Set<ServiceType> = emptySet(),
                    val physicalLocation: PhysicalLocation? = null)
