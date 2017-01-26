package net.corda.node.services

import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.expect
import net.corda.testing.node.MockNetwork
import org.junit.Test
import kotlin.test.assertEquals

class InMemoryNetworkMapCacheTest {
    private val network = MockNetwork()

    @Test
    fun registerWithNetwork() {
        val (n0, n1) = network.createTwoNodes()
        val future = n1.services.networkMapCache.addMapService(n1.net, n0.info.address, false, null)
        network.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `key collision`() {
        val keyPair = generateKeyPair()
        val nodeA = network.createNode(null, -1, MockNetwork.DefaultFactory, true, "Node A", keyPair, ServiceInfo(NetworkMapService.type))
        val nodeB = network.createNode(null, -1, MockNetwork.DefaultFactory, true, "Node B", keyPair, ServiceInfo(NetworkMapService.type))

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.netMapCache.getNodeByLegalIdentityKey(keyPair.public.composite), nodeA.info)

        databaseTransaction(nodeA.database) {
            nodeA.netMapCache.addNode(nodeB.info)
        }
        // Now both nodes match, so it throws an error
        expect<IllegalStateException> {
            nodeA.netMapCache.getNodeByLegalIdentityKey(keyPair.public.composite)
        }
    }
}
