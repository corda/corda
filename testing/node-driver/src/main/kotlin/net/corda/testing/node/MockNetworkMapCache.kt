package net.corda.testing.node

import co.paralleluniverse.common.util.VisibleForTesting
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.NonEmptySet
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.testing.getTestPartyAndCertificate
import rx.Observable
import rx.subjects.PublishSubject
import java.math.BigInteger

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache(serviceHub: ServiceHubInternal) : PersistentNetworkMapCache(serviceHub) {
    private companion object {
        val BANK_C = getTestPartyAndCertificate(CordaX500Name(organisation = "Bank C", locality = "London", country = "GB"), entropyToKeyPair(BigInteger.valueOf(1000)).public)
        val BANK_D = getTestPartyAndCertificate(CordaX500Name(organisation = "Bank D", locality = "London", country = "GB"), entropyToKeyPair(BigInteger.valueOf(2000)).public)
        val BANK_C_ADDR = NetworkHostAndPort("bankC", 8080)
        val BANK_D_ADDR = NetworkHostAndPort("bankD", 8080)
    }

    override val changed: Observable<NetworkMapCache.MapChange> = PublishSubject.create<NetworkMapCache.MapChange>()

    init {
        val mockNodeA = NodeInfo(listOf(BANK_C_ADDR), listOf(BANK_C), 1, serial = 1L)
        val mockNodeB = NodeInfo(listOf(BANK_D_ADDR), listOf(BANK_D), 1, serial = 1L)
        partyNodes.add(mockNodeA)
        partyNodes.add(mockNodeB)
        runWithoutMapService()
    }

    /**
     * Directly add a registration to the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun addRegistration(node: NodeInfo) {
        val previousIndex = partyNodes.indexOfFirst { it.legalIdentitiesAndCerts == node.legalIdentitiesAndCerts }
        if (previousIndex != -1) partyNodes[previousIndex] = node
        else partyNodes.add(node)
    }

    /**
     * Directly remove a registration from the internal cache. DOES NOT fire the change listeners, as it's
     * not a change being received.
     */
    @VisibleForTesting
    fun deleteRegistration(legalIdentity: Party): Boolean {
        return partyNodes.removeIf { legalIdentity.owningKey in it.legalIdentitiesAndCerts.map { it.owningKey } }
    }
}
