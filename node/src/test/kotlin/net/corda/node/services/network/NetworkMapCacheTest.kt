package net.corda.node.services.network

import net.corda.core.crypto.toStringShort
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.serialize
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
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

    @Test(timeout=300_000)
	fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = entropy))
        val alice = aliceNode.info.singleIdentity()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(alice.owningKey).singleOrNull(), aliceNode.info)
        val bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, entropyRoot = entropy))
        val bob = bobNode.info.singleIdentity()
        assertEquals(alice, bob)

        aliceNode.services.networkMapCache.addOrUpdateNode(bobNode.info)
        // The details of node B write over those for node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(alice.owningKey).singleOrNull(), bobNode.info)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun getPeerByLegalName() {
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bobCache: NetworkMapCache = bobNode.services.networkMapCache
        val expected = aliceNode.info.singleIdentity()

        val actual = bobNode.database.transaction { bobCache.getPeerByLegalName(ALICE_NAME) }
        assertEquals(expected, actual)
    }

    @Test(timeout=300_000)
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

        bobCacheInternal.addOrUpdateNode(aliceNode.info)

        assertEquals(aliceNode.info.singleIdentity(), bobCache.getPeerByLegalName(ALICE_NAME))
        assertEquals(aliceNode.info, bobCache.getNodesByLegalIdentityKey(aliceNode.info.singleIdentity().owningKey).single())
    }

    @Test(timeout=300_000)
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

    @Test(timeout = 300_000)
    fun `replace node with the same key but different certificate`() {
        val bobCache = mockNet.createPartyNode(BOB_NAME).services.networkMapCache

        // Add node info with original key and certificate
        val aliceInfo = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = BigInteger.valueOf(70))).info
        val aliceKey = aliceInfo.legalIdentities.single().owningKey
        val aliceCertificate = aliceInfo.legalIdentitiesAndCerts.single()
        bobCache.addOrUpdateNode(aliceInfo)

        // Check database
        assertThat(bobCache.getNodeByLegalName(ALICE_NAME)).isEqualTo(aliceInfo)
        assertThat(bobCache.getNodesByOwningKeyIndex(aliceKey.toStringShort())).containsOnly(aliceInfo)
        // Check cache
        assertThat(bobCache.getNodeByHash(aliceInfo.serialize().hash)).isEqualTo(aliceInfo)
        assertThat(bobCache.getPeerCertificateByLegalName(ALICE_NAME)).isEqualTo(aliceCertificate)
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceKey)).containsOnly(aliceInfo)

        // Update node info with new key and certificate
        val aliceInfo2 = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = BigInteger.valueOf(70))).info
        val aliceKey2 = aliceInfo2.legalIdentities.single().owningKey
        val aliceCertificate2 = aliceInfo2.legalIdentitiesAndCerts.single()
        bobCache.addOrUpdateNode(aliceInfo2)

        // Check that keys are the same and certificates are different
        assertThat(aliceKey).isEqualTo(aliceKey2)
        assertThat(aliceCertificate.certificate).isNotEqualTo(aliceCertificate2.certificate)

        // Check new entry
        assertThat(bobCache.getNodeByLegalName(ALICE_NAME)).isEqualTo(aliceInfo2)
        assertThat(bobCache.getNodesByOwningKeyIndex(aliceKey2.toStringShort())).containsOnly(aliceInfo2)
        assertThat(bobCache.getNodeByHash(aliceInfo2.serialize().hash)).isEqualTo(aliceInfo2)
        assertThat(bobCache.getPeerCertificateByLegalName(ALICE_NAME)).isEqualTo(aliceCertificate2)
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceKey2)).containsOnly(aliceInfo2)
        // Check old entry
        assertThat(bobCache.getNodeByHash(aliceInfo.serialize().hash)).isNull()
    }

    @Test(timeout = 300_000)
    fun `replace node with the same name but different key and certificate`() {
        val bobCache = mockNet.createPartyNode(BOB_NAME).services.networkMapCache

        // Add node info with original key and certificate
        val aliceInfo = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = BigInteger.valueOf(70))).info
        val aliceKey = aliceInfo.legalIdentities.single().owningKey
        val aliceCertificate = aliceInfo.legalIdentitiesAndCerts.single()
        bobCache.addOrUpdateNode(aliceInfo)

        // Check database
        assertThat(bobCache.getNodeByLegalName(ALICE_NAME)).isEqualTo(aliceInfo)
        assertThat(bobCache.getNodesByOwningKeyIndex(aliceKey.toStringShort())).containsOnly(aliceInfo)
        // Check cache
        assertThat(bobCache.getNodeByHash(aliceInfo.serialize().hash)).isEqualTo(aliceInfo)
        assertThat(bobCache.getPeerCertificateByLegalName(ALICE_NAME)).isEqualTo(aliceCertificate)
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceKey)).containsOnly(aliceInfo)

        // Update node info with new key and certificate
        val aliceInfo2 = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, entropyRoot = BigInteger.valueOf(71))).info
        val aliceKey2 = aliceInfo2.legalIdentities.single().owningKey
        val aliceCertificate2 = aliceInfo2.legalIdentitiesAndCerts.single()
        bobCache.addOrUpdateNode(aliceInfo2)

        // Check that keys and certificates are different
        assertThat(aliceKey).isNotEqualTo(aliceKey2)
        assertThat(aliceCertificate.certificate).isNotEqualTo(aliceCertificate2.certificate)

        // Check new entry
        assertThat(bobCache.getNodeByLegalName(ALICE_NAME)).isEqualTo(aliceInfo2)
        assertThat(bobCache.getNodesByOwningKeyIndex(aliceKey2.toStringShort())).containsOnly(aliceInfo2)
        assertThat(bobCache.getNodeByHash(aliceInfo2.serialize().hash)).isEqualTo(aliceInfo2)
        assertThat(bobCache.getPeerCertificateByLegalName(ALICE_NAME)).isEqualTo(aliceCertificate2)
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceKey2)).containsOnly(aliceInfo2)
        // Check old entry
        assertThat(bobCache.getNodesByOwningKeyIndex(aliceKey.toStringShort())).isEmpty()
        assertThat(bobCache.getNodeByHash(aliceInfo.serialize().hash)).isNull()
        assertThat(bobCache.getNodesByLegalIdentityKey(aliceKey)).isEmpty()
    }
}
