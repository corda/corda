package net.corda.node.services.network

import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockNetwork
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class InMemoryNetworkMapCacheTest {
    private val mockNet = MockNetwork()

    @Test
    fun registerWithNetwork() {
        val (n0, n1) = mockNet.createTwoNodes()
        val future = n1.services.networkMapCache.addMapService(n1.network, n0.info.address, false, null)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val nodeA = mockNet.createNode(null, -1, MockNetwork.DefaultFactory, true, ALICE.name, null, entropy, ServiceInfo(NetworkMapService.type))
        val nodeB = mockNet.createNode(null, -1, MockNetwork.DefaultFactory, true, BOB.name, null, entropy, ServiceInfo(NetworkMapService.type))
        assertEquals(nodeA.info.legalIdentity, nodeB.info.legalIdentity)

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.netMapCache.getNodeByLegalIdentityKey(nodeA.info.legalIdentity.owningKey), nodeA.info)

        nodeA.database.transaction {
            nodeA.netMapCache.addNode(nodeB.info)
        }
        // The details of node B write over those for node A
        assertEquals(nodeA.netMapCache.getNodeByLegalIdentityKey(nodeA.info.legalIdentity.owningKey), nodeB.info)
    }
}
