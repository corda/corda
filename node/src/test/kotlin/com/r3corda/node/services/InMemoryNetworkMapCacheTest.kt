package com.r3corda.node.services

import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.testing.expect
import com.r3corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class InMemoryNetworkMapCacheTest {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    @Test
    fun registerWithNetwork() {
        val (n0, n1) = network.createTwoNodes()

        val future = n1.services.networkMapCache.addMapService(n1.net, n0.info.address, false, null)
        network.runNetwork()
        future.get()
    }

    @Test
    fun `key collision`() {
        val keyPair = generateKeyPair()
        val nodeA = network.createNode(null, -1, MockNetwork.DefaultFactory, true, "Node A", keyPair, ServiceInfo(NetworkMapService.type))
        val nodeB = network.createNode(null, -1, MockNetwork.DefaultFactory, true, "Node B", keyPair, ServiceInfo(NetworkMapService.type))

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.netMapCache.getNodeByPublicKey(keyPair.public), nodeA.info)

        nodeA.netMapCache.addNode(nodeB.info)
        // Now both nodes match, so it throws an error
        expect<IllegalStateException> {
            nodeA.netMapCache.getNodeByPublicKey(keyPair.public)
        }
    }
}
