package net.corda.node.services.network

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.toBase58String
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.unwrap
import net.corda.node.internal.Node
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.CHARLIE
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PersistentNetworkMapCacheTest: NodeBasedTest() {
    val partiesList = listOf(DUMMY_NOTARY, ALICE, BOB)
    val addressesMap: HashMap<X500Name, NetworkHostAndPort> = HashMap()
    val infos: MutableSet<NodeInfo> = HashSet()

    @Before
    fun start() {
        val nodes = startNodesWithPort(partiesList)
        nodes.forEach { it.nodeReadyFuture.get() } // Need to wait for network map registration, as these tests are ran without waiting.
        nodes.forEach {
            infos.add(it.info)
            addressesMap[it.info.legalIdentity.name] = it.info.addresses[0]
            it.stop() // We want them to communicate with NetworkMapService to save data to cache.
        }
    }

    @Test
    fun `restart node with DB map cache and no network map`() {
        val alice = startNodesWithPort(listOf(ALICE), noNetworkMap = true)[0]
        val partyNodes = alice.services.networkMapCache.partyNodes
        assert(NetworkMapService.type !in alice.info.advertisedServices.map { it.info.type })
        assertEquals(null, alice.inNodeNetworkMapService)
        assertEquals(infos.size, partyNodes.size)
        assertEquals(infos, partyNodes.toSet())
        alice.stop()
    }

    @Test
    fun `start 2 nodes without pointing at NetworkMapService and communicate with each other`() {
        val parties = partiesList.subList(1, partiesList.size)
        val nodes = startNodesWithPort(parties, noNetworkMap = true)
        assert(nodes.all { it.inNodeNetworkMapService == null })
        assert(nodes.all { NetworkMapService.type !in it.info.advertisedServices.map { it.info.type } })
        nodes.forEach {
            val partyNodes = it.services.networkMapCache.partyNodes
            assertEquals(infos.size, partyNodes.size)
            assertEquals(infos, partyNodes.toSet())
        }
        checkConnectivity(nodes)
    }

    @Test
    fun `start 2 nodes pointing at NetworkMapService but don't start network map node`() {
        val parties = partiesList.subList(1, partiesList.size)
        val nodes = startNodesWithPort(parties, noNetworkMap = false)
        assert(nodes.all { it.inNodeNetworkMapService == null })
        assert(nodes.all { NetworkMapService.type !in it.info.advertisedServices.map { it.info.type } })
        nodes.forEach {
            val partyNodes = it.services.networkMapCache.partyNodes
            assertEquals(infos.size, partyNodes.size)
            assertEquals(infos, partyNodes.toSet())
        }
        checkConnectivity(nodes)
    }

    @Test
    fun `start node and network map communicate`() {
        val parties = partiesList.subList(0, 2)
        val nodes = startNodesWithPort(parties, noNetworkMap = false)
        checkConnectivity(nodes)
    }

    @Test
    fun `start node without networkMapService and no database - fail`() {
        assertFails { startNode(CHARLIE.name, noNetworkMap = true) }
    }

    @Test
    fun `new node joins network without network map started`() {
        val parties = partiesList.subList(1, partiesList.size)
        // Start 2 nodes pointing at network map, but don't start network map service.
        val otherNodes = startNodesWithPort(parties, noNetworkMap = false)
        otherNodes.forEach { node ->
            assert(infos.any { it == node.info })
        }
        // Start node that is not in databases of other nodes. Point to NMS. Which has't started yet.
        val charlie = startNodesWithPort(listOf(CHARLIE), noNetworkMap = false)[0]
        otherNodes.forEach {
            assert(charlie.info !in it.services.networkMapCache.partyNodes)
        }
        // Start Network Map and see that charlie node appears in caches.
        val nms = startNodesWithPort(listOf(DUMMY_NOTARY), noNetworkMap = false)[0]
        nms.startupComplete.get()
        assert(nms.inNodeNetworkMapService != null)
        assert(infos.any {it == nms.info})
        otherNodes.forEach {
            assert(nms.info in it.services.networkMapCache.partyNodes)
        }
        otherNodes.forEach {
            it.nodeReadyFuture.get()
        }
        charlie.nodeReadyFuture.get() // Finish registration.
        checkConnectivity(listOf(otherNodes[0], nms)) // Checks connectivity from A to NMS.
        val cacheA = otherNodes[0].services.networkMapCache.partyNodes
        val cacheB = otherNodes[1].services.networkMapCache.partyNodes
        val cacheC = charlie.services.networkMapCache.partyNodes
        assertEquals(4, cacheC.size) // Charlie fetched data from NetworkMap
        assert(charlie.info in cacheB) // Other nodes also fetched data from Network Map with node C.
        assertEquals(cacheA.toSet(), cacheB.toSet())
        assertEquals(cacheA.toSet(), cacheC.toSet())
    }

    // HELPERS
    // Helper function to restart nodes with the same host and port.
    private fun startNodesWithPort(nodesToStart: List<Party>, noNetworkMap: Boolean = false): List<Node> {
        return nodesToStart.map { party ->
            val configOverrides = addressesMap[party.name]?.let { mapOf("p2pAddress" to it.toString()) } ?: emptyMap()
            if (party == DUMMY_NOTARY) startNetworkMapNode(party.name, configOverrides = configOverrides)
            else startNode(party.name,
                    configOverrides = configOverrides,
                    noNetworkMap = noNetworkMap,
                    waitForConnection = false).getOrThrow()
        }
    }

    // Check that nodes are functional, communicate each with each.
    private fun checkConnectivity(nodes: List<Node>) {
        nodes.forEach { it.registerInitiatedFlow(SendBackFlow::class.java) }
        nodes.forEach { node1 ->
            nodes.forEach { node2 ->
                val resultFuture = node1.services.startFlow(SendFlow(node2.info.legalIdentity)).resultFuture
                assertThat(resultFuture.getOrThrow()).isEqualTo("Hello!")
            }
        }
    }

    @InitiatingFlow
    private class SendFlow(val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            println("SEND FLOW to $otherParty")
            println("Party key ${otherParty.owningKey.toBase58String()}")
            return sendAndReceive<String>(otherParty, "Hi!").unwrap { it }
        }
    }

    @InitiatedBy(SendFlow::class)
    private class SendBackFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            println("SEND BACK FLOW to $otherParty")
            println("Party key ${otherParty.owningKey.toBase58String()}")
            send(otherParty, "Hello!")
        }
    }
}
