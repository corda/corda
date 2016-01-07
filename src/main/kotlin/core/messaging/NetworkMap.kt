/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import core.Party

/** Info about a network node that has is operated by some sort of verified identity. */
data class LegallyIdentifiableNode(val address: SingleMessageRecipient, val identity: Party)

/**
 * A NetworkMap allows you to look up various types of services provided by nodes on the network, and find node
 * addresses given legal identities (NB: not all nodes may have legal identities).
 *
 * A real implementation would probably do RPCs to a lookup service which might in turn be backed by a ZooKeeper
 * cluster or equivalent.
 *
 * For now, this class is truly minimal.
 */
interface NetworkMap {
    val timestampingNodes: List<LegallyIdentifiableNode>
}
