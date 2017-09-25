package net.corda.node.services.network

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class NetworkMapCacheTest {
    lateinit var mockNet: MockNetwork

    @Before
    fun setUp() {
        mockNet = MockNetwork()
    }

    @After
    fun teardown() {
        mockNet.stopNodes()
    }

    @Test
    fun registerWithNetwork() {
        val nodes = mockNet.createSomeNodes(1)
        val n0 = nodes.mapNode
        val n1 = nodes.partyNodes[0]
        val future = n1.services.networkMapCache.addMapService(n1.network, n0.network.myAddress, false, null)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val nodeA = mockNet.createNode(nodeFactory = MockNetwork.DefaultFactory, legalName = ALICE.name, entropyRoot = entropy, advertisedServices = ServiceInfo(NetworkMapService.type))
        val nodeB = mockNet.createNode(nodeFactory = MockNetwork.DefaultFactory, legalName = BOB.name, entropyRoot = entropy, advertisedServices = ServiceInfo(NetworkMapService.type))
        assertEquals(nodeA.info.chooseIdentity(), nodeB.info.chooseIdentity())

        mockNet.runNetwork()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.services.networkMapCache.getNodesByLegalIdentityKey(nodeA.info.chooseIdentity().owningKey).singleOrNull(), nodeA.info)

        nodeA.services.networkMapCache.addNode(nodeB.info)
        // The details of node B write over those for node A
        assertEquals(nodeA.services.networkMapCache.getNodesByLegalIdentityKey(nodeA.info.chooseIdentity().owningKey).singleOrNull(), nodeB.info)
    }

    @Test
    fun `getNodeByLegalIdentity`() {
        val nodes = mockNet.createSomeNodes(1)
        val n0 = nodes.mapNode
        val n1 = nodes.partyNodes[0]
        val node0Lookup = n0.nodeLookup
        val expected = n1.info

        mockNet.runNetwork()
        val actual = n0.database.transaction { node0Lookup.getNodeByLegalIdentity(n1.info.chooseIdentity()) }
        assertEquals(expected, actual)

        // TODO: Should have a test case with anonymous lookup
    }

    @Test
    fun `remove node from cache`() {
        val nodes = mockNet.createSomeNodes(1)
        val n0 = nodes.mapNode
        val n1 = nodes.partyNodes[0]
        val n0Identity = n0.info.chooseIdentity()
        val n1Identity = n1.info.chooseIdentity()
        val node0Lookup = n0.nodeLookup
        val node0Cache = n0.services.networkMapCache
        mockNet.runNetwork()
        n0.database.transaction {
            assertThat(node0Lookup.getNodeByLegalIdentity(n1Identity) != null)
            node0Cache.removeNode(n1.info)
            assertThat(node0Lookup.getNodeByLegalIdentity(n1Identity) == null)
            assertThat(node0Lookup.getNodeByLegalIdentity(n0Identity) != null)
            assertThat(node0Cache.getNodeByLegalName(n1Identity.name) == null)
        }
    }
}
