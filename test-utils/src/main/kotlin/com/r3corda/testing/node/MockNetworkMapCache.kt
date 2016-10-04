package com.r3corda.testing.node

import co.paralleluniverse.common.util.VisibleForTesting
import com.r3corda.core.crypto.DummyPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import rx.Observable
import rx.subjects.PublishSubject

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache() : com.r3corda.node.services.network.InMemoryNetworkMapCache() {
    override val changed: Observable<NetworkMapCache.MapChange> = PublishSubject.create<NetworkMapCache.MapChange>()

    data class MockAddress(val id: String): SingleMessageRecipient

    init {
        val mockNodeA = NodeInfo(MockAddress("bankC:8080"), Party("Bank C", DummyPublicKey("Bank C")))
        val mockNodeB = NodeInfo(MockAddress("bankD:8080"), Party("Bank D", DummyPublicKey("Bank D")))
        registeredNodes[mockNodeA.legalIdentity] = mockNodeA
        registeredNodes[mockNodeB.legalIdentity] = mockNodeB
        runWithoutMapService()
    }

    /**
     * Directly add a registration to the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun addRegistration(node: NodeInfo) {
        registeredNodes[node.legalIdentity] = node
    }

    /**
     * Directly remove a registration from the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun deleteRegistration(legalIdentity: Party) : Boolean {
        return registeredNodes.remove(legalIdentity) != null
    }
}