package net.corda.core.node.services

import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry

sealed class PartyInfo(val party: Party) {
    class Node(val node: NodeInfo) : PartyInfo(node.legalIdentity)
    class Service(val service: ServiceEntry) : PartyInfo(service.identity)
}