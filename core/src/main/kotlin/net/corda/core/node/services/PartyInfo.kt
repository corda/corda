package net.corda.core.node.services

import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry

/**
 * Holds information about a [Party], which may refer to either a specific node or a service.
 */
sealed class PartyInfo(val party: Party) {
    class Node(val node: NodeInfo) : PartyInfo(node.legalIdentity)
    class Service(val service: ServiceEntry) : PartyInfo(service.identity)
}