/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import core.Party
import java.util.*

/** Info about a network node that has is operated by some sort of verified identity. */
data class LegallyIdentifiableNode(val address: SingleMessageRecipient, val identity: Party)

/**
 * A network map contains lists of nodes on the network along with information about their identity keys, services
 * they provide and host names or IP addresses where they can be connected to. A reasonable architecture for the
 * network map service might be one like the Tor directory authorities, where several nodes linked by RAFT or Paxos
 * elect a leader and that leader distributes signed documents describing the network layout. Those documents can
 * then be cached by every node and thus a network map can be retrieved given only a single successful peer connection.
 *
 * This interface assumes fast, synchronous access to an in-memory map.
*/
interface NetworkMap {
    val timestampingNodes: List<LegallyIdentifiableNode>
}

// TODO: Move this to the test tree once a real network map is implemented and this scaffolding is no longer needed.
class MockNetworkMap : NetworkMap {
    override val timestampingNodes = Collections.synchronizedList(ArrayList<LegallyIdentifiableNode>())
}
