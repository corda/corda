package net.corda.node.services.network

import net.corda.core.node.services.NetworkMapCache
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

class NetworkMapCacheTest {
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
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), aliceNode.info)
        val bobNode = mockNet.createNode(MockNodeParameters(legalName = BOB.name, entropyRoot = entropy))
        assertEquals(aliceNode.info.chooseIdentity(), bobNode.info.chooseIdentity())

        aliceNode.services.networkMapCache.addNode(bobNode.info)
        // The details of node B write over those for node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), bobNode.info)
    }

    @Test
    fun `getNodeByLegalIdentity`() {
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info

        mockNet.runNetwork()
        val actual = bobNode.database.transaction { bobCache.getNodeByLegalIdentity(aliceNode.info.chooseIdentity()) }
        assertEquals(expected, actual)

        // TODO: Should have a test case with anonymous lookup
    }

    @Test
    fun `getPeerByLegalName`() {
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info.legalIdentities.single()

        mockNet.runNetwork()
        val actual = bobNode.database.transaction { bobCache.getPeerByLegalName(ALICE.name) }
        assertEquals(expected, actual)
    }

    @Test
    fun `remove node from cache`() {
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val bobLegalIdentity = bobNode.info.chooseIdentity()
        val alice = aliceNode.info.chooseIdentity()
        val bobCache = bobNode.services.networkMapCache
        mockNet.runNetwork()
        bobNode.database.transaction {
            assertThat(bobCache.getNodeByLegalIdentity(alice) != null)
            bobCache.removeNode(aliceNode.info)
            assertThat(bobCache.getNodeByLegalIdentity(alice) == null)
            assertThat(bobCache.getNodeByLegalIdentity(bobLegalIdentity) != null)
            assertThat(bobCache.getNodeByLegalName(alice.name) == null)
        }
    }
}
