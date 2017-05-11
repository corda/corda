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
        // Create two nodes with the same name, but different keys
        val nodeA = network.createNode(null, -1, MockNetwork.DefaultFactory, true, ALICE.name, null, ServiceInfo(NetworkMapService.type))
        val nodeB = network.createNode(null, -1, MockNetwork.DefaultFactory, true, ALICE.name, null, ServiceInfo(NetworkMapService.type))
        assertEquals(nodeA.info.legalIdentity, nodeB.info.legalIdentity)

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.info, nodeA.netMapCache.getNodeByLegalName(nodeA.info.legalIdentity.name))

        nodeA.database.transaction {
            nodeA.netMapCache.addNode(nodeB.info)
        }
        // The details of node B write over those for node A
        assertEquals(nodeB.info, nodeA.netMapCache.getNodeByLegalName(nodeA.info.legalIdentity.name))
    }
}
