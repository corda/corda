package net.corda.node.services.network

import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

// TODO Clean up these tests, they were written with old network map design in place.
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
            addressesMap[it.info.singleIdentity().name] = it.info.addresses[0]
            it.dispose() // We want them to communicate with NetworkMapService to save data to cache.
        }
    }

    @Test
    fun `unknown legal name`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netMapCache = alice.services.networkMapCache
        assertThat(netMapCache.getNodesByLegalName(DUMMY_NOTARY_NAME)).isEmpty()
        assertThat(netMapCache.getNodeByLegalName(DUMMY_NOTARY_NAME)).isNull()
        assertThat(netMapCache.getPeerByLegalName(DUMMY_NOTARY_NAME)).isNull()
        assertThat(netMapCache.getPeerCertificateByLegalName(DUMMY_NOTARY_NAME)).isNull()
    }

    @Test
    fun `nodes in distributed service`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netMapCache = alice.services.networkMapCache

        val distributedIdentity = TestIdentity(DUMMY_NOTARY_NAME).identity
        val distServiceNodeInfos = (1..2).map {
            val nodeInfo = NodeInfo(
                    addresses = listOf(NetworkHostAndPort("localhost", 1000 + it)),
                    legalIdentitiesAndCerts = listOf(TestIdentity.fresh("Org-$it").identity, distributedIdentity),
                    platformVersion = 3,
                    serial = 1
            )
            netMapCache.addNode(nodeInfo)
            nodeInfo
        }

        assertThat(netMapCache.getNodesByLegalName(DUMMY_NOTARY_NAME)).containsOnlyElementsOf(distServiceNodeInfos)
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { netMapCache.getNodeByLegalName(DUMMY_NOTARY_NAME) }
                .withMessageContaining(DUMMY_NOTARY_NAME.toString())
    }

    @Test
    fun `get nodes by owning key and by name`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        val res = netCache.getNodeByLegalIdentity(alice.info.singleIdentity())
        assertEquals(alice.info, res)
        val res2 = netCache.getNodeByLegalName(DUMMY_REGULATOR.name)
        assertEquals(infos.singleOrNull { DUMMY_REGULATOR.name in it.legalIdentities.map { it.name } }, res2)
    }

    @Test
    fun `get nodes by address`() {
        val alice = startNodesWithPort(listOf(ALICE))[0]
        val netCache = alice.services.networkMapCache
        val res = netCache.getNodeByAddress(alice.info.addresses[0])
        assertEquals(alice.info, res)
    }

    // This test has to be done as normal node not mock, because MockNodes don't have addresses.
    @Test
    fun `insert two node infos with the same host and port`() {
        val aliceNode = startNode(ALICE_NAME)
        val charliePartyCert = getTestPartyAndCertificate(CHARLIE_NAME, generateKeyPair().public)
        val aliceCache = aliceNode.services.networkMapCache
        aliceCache.addNode(aliceNode.info.copy(legalIdentitiesAndCerts = listOf(charliePartyCert)))
        val res = aliceCache.allNodes.filter { aliceNode.info.addresses[0] in it.addresses }
        assertEquals(2, res.size)
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
