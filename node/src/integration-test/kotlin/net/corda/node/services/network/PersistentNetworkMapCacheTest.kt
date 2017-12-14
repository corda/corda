package net.corda.node.services.network

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.TestIdentity
import net.corda.testing.chooseIdentity
import net.corda.testing.node.internal.NodeBasedTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PersistentNetworkMapCacheTest : NodeBasedTest() {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val BOB = TestIdentity(BOB_NAME, 80).party
        val DUMMY_REGULATOR = TestIdentity(CordaX500Name("Regulator A", "Paris", "FR"), 100).party
    }

    private val partiesList = listOf(DUMMY_REGULATOR, ALICE, BOB)
    private val addressesMap = HashMap<CordaX500Name, NetworkHostAndPort>()
    private val infos = HashSet<NodeInfo>()

    @Before
    fun start() {
        val nodes = startNodesWithPort(partiesList)
        nodes.forEach {
            infos.add(it.info)
            addressesMap[it.info.chooseIdentity().name] = it.info.addresses[0]
            it.dispose() // We want them to communicate with NetworkMapService to save data to cache.
        }
    }

    @Test
    fun `get nodes by owning key and by name`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        alice.database.transaction {
            val res = netCache.getNodeByLegalIdentity(alice.info.chooseIdentity())
            assertEquals(alice.info, res)
            val res2 = netCache.getNodeByLegalName(DUMMY_REGULATOR.name)
            assertEquals(infos.singleOrNull { DUMMY_REGULATOR.name in it.legalIdentities.map { it.name } }, res2)
        }
    }

    @Test
    fun `get nodes by address`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        alice.database.transaction {
            val res = netCache.getNodeByAddress(alice.info.addresses[0])
            assertEquals(alice.info, res)
        }
    }

    @Test
    fun `restart node with DB map cache`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val partyNodes = alice.services.networkMapCache.allNodes
        assertEquals(infos.size, partyNodes.size)
        assertEquals(infos.flatMap { it.legalIdentities }.toSet(), partyNodes.flatMap { it.legalIdentities }.toSet())
    }

    // HELPERS
    // Helper function to restart nodes with the same host and port.
    private fun startNodesWithPort(nodesToStart: List<Party>, customRetryIntervalMs: Long? = null): List<StartedNode<Node>> {
        return nodesToStart.map { party ->
            val configOverrides = (addressesMap[party.name]?.let { mapOf("p2pAddress" to it.toString()) } ?: emptyMap()) +
                    (customRetryIntervalMs?.let { mapOf("activeMQServer.bridge.retryIntervalMs" to it.toString()) } ?: emptyMap())
            startNode(party.name, configOverrides = configOverrides)
        }
    }
}
