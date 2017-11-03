package net.corda.node.services.network

import net.corda.core.node.services.NetworkMapCacheBase
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class NetworkMapCacheBaseTest {
    val mockNet: MockNetwork = MockNetwork()

    @After
    fun teardown() {
        mockNet.stopNodes()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val aliceNode = mockNet.createNode(MockNodeParameters(legalName = ALICE.name, entropyRoot = entropy))
        mockNet.runNetwork()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(aliceNode.services.networkMapCacheBase.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), aliceNode.info)
        val bobNode = mockNet.createNode(MockNodeParameters(legalName = BOB.name, entropyRoot = entropy))
        assertEquals(aliceNode.info.chooseIdentity(), bobNode.info.chooseIdentity())

        aliceNode.services.networkMapCacheBase.addNode(bobNode.info)
        // The details of node B write over those for node A
        assertEquals(aliceNode.services.networkMapCacheBase.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), bobNode.info)
    }

    @Test
    fun `getPeerByLegalName`() {
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val notaryCacheBase: NetworkMapCacheBase = notaryNode.services.networkMapCacheBase
        val expected = aliceNode.info.legalIdentities.single()

        mockNet.runNetwork()
        val actual = notaryNode.database.transaction { notaryCacheBase.getPeerByLegalName(ALICE.name) }
        assertEquals(expected, actual)
    }

    @Test
    fun `remove node from cache`() {
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val notaryLegalIdentity = notaryNode.info.chooseIdentity()
        val alice = aliceNode.info.chooseIdentity()
        val notaryCache = notaryNode.services.networkMapCacheBase
        mockNet.runNetwork()
        notaryNode.database.transaction {
            assertThat(notaryCache.getNodeByLegalName(alice.name)).isNotNull()
            notaryCache.removeNode(aliceNode.info)
            assertThat(notaryCache.getNodeByLegalName(alice.name)).isNull()
            assertThat(notaryCache.getNodeByLegalName(notaryNode.info.legalIdentities.first().name)).isNotNull()
            assertThat(notaryCache.getNodeByLegalName(alice.name)).isNull()
        }
    }
}
