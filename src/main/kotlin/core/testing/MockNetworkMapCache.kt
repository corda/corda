/*
 * Copyright 2016 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */
package core.testing

import core.Party
import core.crypto.DummyPublicKey
import core.messaging.SingleMessageRecipient
import core.node.services.NetworkMapCache
import core.node.services.NodeInfo
import java.util.*

class MockNetworkMapCache : NetworkMapCache {
    data class MockAddress(val id: String) : SingleMessageRecipient

    override val timestampingNodes = Collections.synchronizedList(ArrayList<NodeInfo>())
    override val ratesOracleNodes = Collections.synchronizedList(ArrayList<NodeInfo>())
    override val partyNodes = Collections.synchronizedList(ArrayList<NodeInfo>())
    override val regulators = Collections.synchronizedList(ArrayList<NodeInfo>())

    init {
        partyNodes.add(NodeInfo(MockAddress("bankC:8080"), Party("Bank C", DummyPublicKey("Bank C"))))
        partyNodes.add(NodeInfo(MockAddress("bankD:8080"), Party("Bank D", DummyPublicKey("Bank D"))))
    }
}