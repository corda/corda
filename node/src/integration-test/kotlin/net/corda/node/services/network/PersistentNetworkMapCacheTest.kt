package net.corda.node.services.network

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.*
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.testing.*
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PersistentNetworkMapCacheTest : NodeBasedTest() {
    private val partiesList = listOf(DUMMY_NOTARY, ALICE, BOB)
    private val addressesMap: HashMap<CordaX500Name, NetworkHostAndPort> = HashMap()
    private val infos: MutableSet<NodeInfo> = HashSet()

    @Before
    fun start() {
        val nodes = startNodesWithPort(partiesList)
        nodes.forEach { it.internals.nodeReadyFuture.get() } // Need to wait for network map registration, as these tests are ran without waiting.
        nodes.forEach {
            infos.add(it.info)
            addressesMap[it.info.chooseIdentity().name] = it.info.addresses[0]
            it.dispose() // We want them to communicate with NetworkMapService to save data to cache.
        }
    }

    @Test
    fun `get nodes by owning key and by name, no network map service`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        alice.database.transaction {
            val res = netCache.getNodeByLegalIdentity(alice.info.chooseIdentity())
            assertEquals(alice.info, res)
            val res2 = netCache.getNodeByLegalName(DUMMY_NOTARY.name)
            assertEquals(infos.singleOrNull { DUMMY_NOTARY.name in it.legalIdentitiesAndCerts.map { it.name } }, res2)
        }
    }

    @Test
    fun `get nodes by address no network map service`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        alice.database.transaction {
            val res = netCache.getNodeByAddress(alice.info.addresses[0])
            assertEquals(alice.info, res)
        }
    }

    @Test
    fun `restart node with DB map cache and no network map`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val partyNodes = alice.services.networkMapCache.allNodes
        assertEquals(infos.size, partyNodes.size)
        assertEquals(infos.flatMap { it.legalIdentities }.toSet(), partyNodes.flatMap { it.legalIdentities }.toSet())
    }

    @Test
    fun `start 2 nodes without pointing at NetworkMapService and communicate with each other`() {
        val parties = partiesList.subList(1, partiesList.size)
        val nodes = startNodesWithPort(parties)
        nodes.forEach {
            val partyNodes = it.services.networkMapCache.allNodes
            assertEquals(infos.size, partyNodes.size)
            assertEquals(infos.flatMap { it.legalIdentities }.toSet(), partyNodes.flatMap { it.legalIdentities }.toSet())
        }
        checkConnectivity(nodes)
    }

    @Test
    fun `start 2 nodes pointing at NetworkMapService but don't start network map node`() {
        val parties = partiesList.subList(1, partiesList.size)
        val nodes = startNodesWithPort(parties)
        nodes.forEach {
            val partyNodes = it.services.networkMapCache.allNodes
            assertEquals(infos.size, partyNodes.size)
            assertEquals(infos.flatMap { it.legalIdentities }.toSet(), partyNodes.flatMap { it.legalIdentities }.toSet())
        }
        checkConnectivity(nodes)
    }

    @Test
    fun `start node and network map communicate`() {
        val parties = partiesList.subList(0, 2)
        val nodes = startNodesWithPort(parties)
        checkConnectivity(nodes)
    }

    @Test
    fun `start node without networkMapService and no database - success`() {
        startNode(CHARLIE.name).getOrThrow(2.seconds)
    }

    // HELPERS
    // Helper function to restart nodes with the same host and port.
    private fun startNodesWithPort(nodesToStart: List<Party>, customRetryIntervalMs: Long? = null): List<StartedNode<Node>> {
        return nodesToStart.map { party ->
            val configOverrides = (addressesMap[party.name]?.let { mapOf("p2pAddress" to it.toString()) } ?: emptyMap()) +
                    (customRetryIntervalMs?.let { mapOf("activeMQServer.bridge.retryIntervalMs" to it.toString()) } ?: emptyMap())
            startNode(party.name,
                    configOverrides = configOverrides,
                    waitForConnection = false).getOrThrow()
        }
    }

    // Check that nodes are functional, communicate each with each.
    private fun checkConnectivity(nodes: List<StartedNode<*>>) {
        nodes.forEach { node1 ->
            nodes.forEach { node2 ->
                if (!(node1 === node2)) { // Do not check connectivity to itself
                    node2.internals.registerInitiatedFlow(SendBackFlow::class.java)
                    val resultFuture = node1.services.startFlow(SendFlow(node2.info.chooseIdentity())).resultFuture
                    assertThat(resultFuture.getOrThrow()).isEqualTo("Hello!")
                }
            }
        }
    }

    @InitiatingFlow
    private class SendFlow(val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            logger.info("SEND FLOW to $otherParty")
            logger.info("Party key ${otherParty.owningKey.toBase58String()}")
            val session = initiateFlow(otherParty)
            return session.sendAndReceive<String>("Hi!").unwrap { it }
        }
    }

    @InitiatedBy(SendFlow::class)
    private class SendBackFlow(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("SEND BACK FLOW to ${otherSideSession.counterparty}")
            logger.info("Party key ${otherSideSession.counterparty.owningKey.toBase58String()}")
            otherSideSession.send("Hello!")
        }
    }
}
