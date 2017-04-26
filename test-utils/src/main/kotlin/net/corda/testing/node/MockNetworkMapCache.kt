package net.corda.testing.node

import co.paralleluniverse.common.util.VisibleForTesting
import net.corda.core.crypto.DummyPublicKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.X509Utilities
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.testing.MOCK_VERSION_INFO
import org.bouncycastle.asn1.x500.X500Name
import rx.Observable
import rx.subjects.PublishSubject

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache : InMemoryNetworkMapCache() {
    private companion object {
        val BANK_C = Party(X500Name("CN=Bank C,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"), DummyPublicKey("Bank C"))
        val BANK_D = Party(X500Name("CN=Bank D,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"), DummyPublicKey("Bank D"))
    }

    override val changed: Observable<NetworkMapCache.MapChange> = PublishSubject.create<NetworkMapCache.MapChange>()

    data class MockAddress(val id: String) : SingleMessageRecipient

    init {
        val mockNodeA = NodeInfo(MockAddress("bankC:8080"), BANK_C, MOCK_VERSION_INFO.platformVersion)
        val mockNodeB = NodeInfo(MockAddress("bankD:8080"), BANK_D, MOCK_VERSION_INFO.platformVersion)
        registeredNodes[mockNodeA.legalIdentity.owningKey] = mockNodeA
        registeredNodes[mockNodeB.legalIdentity.owningKey] = mockNodeB
        runWithoutMapService()
    }

    /**
     * Directly add a registration to the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun addRegistration(node: NodeInfo) {
        registeredNodes[node.legalIdentity.owningKey] = node
    }

    /**
     * Directly remove a registration from the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun deleteRegistration(legalIdentity: Party): Boolean {
        return registeredNodes.remove(legalIdentity.owningKey) != null
    }
}
