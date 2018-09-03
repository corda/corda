package net.corda.node.services.network

import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.NetworkMapCache
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkMapCacheTest {
    private val mockNet = InternalMockNetwork()

    @After
    fun teardown() {
        mockNet.stopNodes()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = entropy))
        val alice = aliceNode.info.singleIdentity()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(alice.owningKey).singleOrNull(), aliceNode.info)
        val bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, entropyRoot = entropy))
        val bob = bobNode.info.singleIdentity()
        assertEquals(alice, bob)

        aliceNode.services.networkMapCache.addNode(bobNode.info)
        // The details of node B write over those for node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(alice.owningKey).singleOrNull(), bobNode.info)
    }

    @Test
    fun getNodeByLegalIdentity() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val alice = aliceNode.info.singleIdentity()
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info

        val actual = bobNode.database.transaction { bobCache.getNodeByLegalIdentity(alice) }
        assertEquals(expected, actual)

        // TODO: Should have a test case with anonymous lookup
    }

    @Test
    fun getPeerByLegalName() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info.singleIdentity()

        val actual = bobNode.database.transaction { bobCache.getPeerByLegalName(ALICE_NAME) }
        assertEquals(expected, actual)
    }

    @Test
    fun `caches get cleared on modification`() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info.singleIdentity()

        val actual = bobNode.database.transaction { bobCache.getPeerByLegalName(ALICE_NAME) }
        assertEquals(expected, actual)
        assertEquals(aliceNode.info, bobCache.getNodesByLegalIdentityKey(aliceNode.info.singleIdentity().owningKey).single())

        // remove alice
        val bobCacheInternal = bobCache as NetworkMapCacheInternal
        assertNotNull(bobCacheInternal)
        bobCache.removeNode(aliceNode.info)

        assertNull(bobCache.getPeerByLegalName(ALICE_NAME))
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceNode.info.singleIdentity().owningKey).isEmpty())

        bobCacheInternal.addNode(aliceNode.info)

        assertEquals(aliceNode.info.singleIdentity(), bobCache.getPeerByLegalName(ALICE_NAME))
        assertEquals(aliceNode.info, bobCache.getNodesByLegalIdentityKey(aliceNode.info.singleIdentity().owningKey).single())
    }

    @Test
    fun `remove node from cache`() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bob = bobNode.info.singleIdentity()
        val alice = aliceNode.info.singleIdentity()
        val bobCache = bobNode.services.networkMapCache
        bobNode.database.transaction {
            assertThat(bobCache.getNodeByLegalIdentity(alice) != null)
            bobCache.removeNode(aliceNode.info)
            assertThat(bobCache.getNodeByLegalIdentity(alice) == null)
            assertThat(bobCache.getNodeByLegalIdentity(bob) != null)
            assertThat(bobCache.getNodeByLegalName(alice.name) == null)
        }
    }

    @Test
    fun `add two nodes the same name different keys`() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val aliceCache = aliceNode.services.networkMapCache
        val alicePartyAndCert2 = getTestPartyAndCertificate(ALICE_NAME, generateKeyPair().public)
        aliceCache.addNode(aliceNode.info.copy(legalIdentitiesAndCerts = listOf(alicePartyAndCert2)))
        // This is correct behaviour as we may have distributed service nodes.
        assertEquals(2, aliceCache.getNodesByLegalName(ALICE_NAME).size)
    }
}
