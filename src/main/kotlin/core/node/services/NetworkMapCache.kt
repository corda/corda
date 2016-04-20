package core.node.services

import core.node.NodeInfo

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. A reasonable architecture for the
 * network map service might be one like the Tor directory authorities, where several nodes linked by RAFT or Paxos
 * elect a leader and that leader distributes signed documents describing the network layout. Those documents can
 * then be cached by every node and thus a network map can be retrieved given only a single successful peer connection.
 *
 * This interface assumes fast, synchronous access to an in-memory map.
 */
interface NetworkMapCache {
    val timestampingNodes: List<NodeInfo>
    val ratesOracleNodes: List<NodeInfo>
    val partyNodes: List<NodeInfo>
    val regulators: List<NodeInfo>

    fun nodeForPartyName(name: String): NodeInfo? = partyNodes.singleOrNull { it.identity.name == name }
}
