/*
 * Copyright 2016 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */
package core.testing

import co.paralleluniverse.common.util.VisibleForTesting
import core.Party
import core.crypto.DummyPublicKey
import core.messaging.SingleMessageRecipient
import core.node.services.InMemoryNetworkMapCache
import core.node.NodeInfo

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache() : InMemoryNetworkMapCache() {
    data class MockAddress(val id: String): SingleMessageRecipient

    init {
        var mockNodeA = NodeInfo(MockAddress("bankC:8080"), Party("Bank C", DummyPublicKey("Bank C")))
        var mockNodeB = NodeInfo(MockAddress("bankD:8080"), Party("Bank D", DummyPublicKey("Bank D")))
        registeredNodes[mockNodeA.identity] = mockNodeA
        registeredNodes[mockNodeB.identity] = mockNodeB
    }

    /**
     * Directly add a registration to the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun addRegistration(node: NodeInfo) {
        registeredNodes[node.identity] = node
    }

    /**
     * Directly remove a registration from the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun deleteRegistration(identity: Party) : Boolean {
        return registeredNodes.remove(identity) != null
    }
}