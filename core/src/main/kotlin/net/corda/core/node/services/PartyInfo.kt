package net.corda.core.node.services

import net.corda.core.identity.Party
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Holds information about a [Party], which may refer to either a specific node or a service.
 */
sealed class PartyInfo {
    abstract val party: Party

    data class SingleNode(override val party: Party, val addresses: List<NetworkHostAndPort>) : PartyInfo()
    data class DistributedNode(override val party: Party) : PartyInfo()
}
