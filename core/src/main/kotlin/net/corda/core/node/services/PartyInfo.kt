package net.corda.core.node.services

import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry

/**
 * Holds information about a [Party], which may refer to either a specific node or a service.
 */
sealed class PartyInfo {
    abstract val party: Party

    data class Node(val node: NodeInfo) : PartyInfo() {
        override val party get() = node.legalIdentity
    }

    data class Service(val service: ServiceEntry) : PartyInfo() {
        override val party get() = service.identity
    }
}