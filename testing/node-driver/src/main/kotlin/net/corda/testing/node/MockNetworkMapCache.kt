package net.corda.testing.node

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.utilities.CordaPersistence
import net.corda.testing.getTestPartyAndCertificate
import rx.Observable
import rx.subjects.PublishSubject
import java.math.BigInteger

/**
 * Network map cache with no backing map service.
 */
class MockNetworkMapCache(database: CordaPersistence) : PersistentNetworkMapCache(database, emptyList()) {
    private companion object {
        val BANK_C = getTestPartyAndCertificate(CordaX500Name(organisation = "Bank C", locality = "London", country = "GB"), entropyToKeyPair(BigInteger.valueOf(1000)).public)
        val BANK_D = getTestPartyAndCertificate(CordaX500Name(organisation = "Bank D", locality = "London", country = "GB"), entropyToKeyPair(BigInteger.valueOf(2000)).public)
        val BANK_C_ADDR = NetworkHostAndPort("bankC", 8080)
        val BANK_D_ADDR = NetworkHostAndPort("bankD", 8080)
    }

    override val changed: Observable<NetworkMapCache.MapChange> = PublishSubject.create<NetworkMapCache.MapChange>()
    override val nodeReady: CordaFuture<Void?> get() = doneFuture(null)

    init {
        val mockNodeA = NodeInfo(listOf(BANK_C_ADDR), listOf(BANK_C), 1, serial = 1L)
        val mockNodeB = NodeInfo(listOf(BANK_D_ADDR), listOf(BANK_D), 1, serial = 1L)
        addNode(mockNodeA)
        addNode(mockNodeB)
    }
}

