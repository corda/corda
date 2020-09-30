package net.corda.node.services.identity

import net.corda.core.internal.div
import net.corda.core.utilities.OpaqueBytes
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.USD
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalance
import net.corda.node.services.keys.KeyManagementServiceInternal
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_KEY_ALIAS
import net.corda.nodeapi.internal.storeLegalIdentity
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CertificateRotationTest {
    private val mockNet = InternalMockNetwork(cordappsForAllNodes = FINANCE_CORDAPPS)

    private val ref = OpaqueBytes.of(0x01)

    private val TestStartedNode.party get() = info.legalIdentities.first()

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout = 300_000)
    fun `restart with the same identities`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity

        alice.services.startFlow(CashIssueAndPaymentFlow(300.DOLLARS, ref, alice.party, false, notary))
        alice.services.startFlow(CashIssueAndPaymentFlow(1000.DOLLARS, ref, bob.party, false, notary))
        bob.services.startFlow(CashIssueAndPaymentFlow(300.POUNDS, ref, bob.party, false, notary))
        bob.services.startFlow(CashIssueAndPaymentFlow(1000.POUNDS, ref, alice.party, false, notary))
        mockNet.runNetwork()

        val alice2 = mockNet.restartNode(alice)
        val bob2 = mockNet.restartNode(bob)

        assertEquals(alice.party, alice2.party)
        assertEquals(bob.party, bob2.party)

        alice2.services.startFlow(CashPaymentFlow(300.DOLLARS, bob2.party, false))
        bob2.services.startFlow(CashPaymentFlow(300.POUNDS, alice2.party, false))
        mockNet.runNetwork()
        bob2.services.startFlow(CashPaymentFlow(1300.DOLLARS, alice2.party, false))
        alice2.services.startFlow(CashPaymentFlow(1300.POUNDS, bob2.party, false))
        mockNet.runNetwork()

        assertEquals(1300.DOLLARS, alice2.services.getCashBalance(USD))
        assertEquals(0.POUNDS, alice2.services.getCashBalance(GBP))
        assertEquals(0.DOLLARS, bob2.services.getCashBalance(USD))
        assertEquals(1300.POUNDS, bob2.services.getCashBalance(GBP))
    }

    @Test(timeout = 300_000)
    fun `restart with the rotated key for one node`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity

        alice.services.startFlow(CashIssueAndPaymentFlow(300.DOLLARS, ref, alice.party, false, notary))
        alice.services.startFlow(CashIssueAndPaymentFlow(1000.DOLLARS, ref, bob.party, false, notary))
        bob.services.startFlow(CashIssueAndPaymentFlow(300.POUNDS, ref, bob.party, false, notary))
        bob.services.startFlow(CashIssueAndPaymentFlow(1000.POUNDS, ref, alice.party, false, notary))
        mockNet.runNetwork()

        val oldAliceIdentity = rotateIdentityKey(alice)

        val alice2 = mockNet.restartNode(alice)
        val bob2 = mockNet.restartNode(bob)

        (alice2.services.keyManagementService as KeyManagementServiceInternal).start(listOf(oldAliceIdentity))

        assertNotEquals(alice.party, alice2.party)
        assertEquals(bob.party, bob2.party)

        alice2.services.startFlow(CashPaymentFlow(300.DOLLARS, bob2.party, false))
        bob2.services.startFlow(CashPaymentFlow(300.POUNDS, alice2.party, false))
        mockNet.runNetwork()
        bob2.services.startFlow(CashPaymentFlow(1300.DOLLARS, alice2.party, false))
        alice2.services.startFlow(CashPaymentFlow(1300.POUNDS, bob2.party, false))
        mockNet.runNetwork()

        assertEquals(1300.DOLLARS, alice2.services.getCashBalance(USD))
        assertEquals(0.POUNDS, alice2.services.getCashBalance(GBP))
        assertEquals(0.DOLLARS, bob2.services.getCashBalance(USD))
        assertEquals(1300.POUNDS, bob2.services.getCashBalance(GBP))
    }

    private fun rotateIdentityKey(node: TestStartedNode): Pair<PublicKey, String> {
        val oldIdentityAlias = "old-identity"
        val certStore = CertificateStoreStubs.Signing.withCertificatesDirectory(mockNet.baseDirectory(node) / "certificates").get()
        certStore.update {
            val oldKey = getPrivateKey(NODE_IDENTITY_KEY_ALIAS, DEV_CA_KEY_STORE_PASS)
            setPrivateKey(oldIdentityAlias, oldKey, getCertificateChain(NODE_IDENTITY_KEY_ALIAS), DEV_CA_KEY_STORE_PASS)
        }
        certStore.storeLegalIdentity(NODE_IDENTITY_KEY_ALIAS)
        return certStore[oldIdentityAlias].publicKey to oldIdentityAlias
    }
}