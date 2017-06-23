package net.corda.testing.node

import co.paralleluniverse.common.util.VisibleForTesting
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.Party
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.getTestPartyAndCertificate
import net.corda.node.services.network.InMemoryNetworkMapCache
import net.corda.testing.MOCK_VERSION_INFO
import net.corda.testing.getTestX509Name
import rx.Observable
import rx.subjects.PublishSubject
import java.math.BigInteger

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache(serviceHub: ServiceHub) : InMemoryNetworkMapCache(serviceHub) {
    private companion object {
        val BANK_C = getTestPartyAndCertificate(getTestX509Name("Bank C"), entropyToKeyPair(BigInteger.valueOf(1000)).public)
        val BANK_D = getTestPartyAndCertificate(getTestX509Name("Bank D"), entropyToKeyPair(BigInteger.valueOf(2000)).public)
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
