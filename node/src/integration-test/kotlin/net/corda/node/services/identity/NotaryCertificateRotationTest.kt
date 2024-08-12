package net.corda.node.services.identity

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.workflows.getCashBalance
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.addNotary
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(Parameterized::class)
class NotaryCertificateRotationTest(private val validating: Boolean) {
    private val ref = OpaqueBytes.of(0x01)

    private val TestStartedNode.party get() = info.legalIdentities.first()

    private lateinit var mockNet: InternalMockNetwork

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "validating = {0}")
        fun data() = listOf(false, true)
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test(timeout = 300_000)
    fun `rotate notary identity`() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = FINANCE_CORDAPPS,
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME, validating))
        )
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)

        // Issue states and notarize them with initial notary identity.
        alice.services.startFlow(CashIssueFlow(1000.DOLLARS, ref, mockNet.defaultNotaryIdentity))
        alice.services.startFlow(CashIssueAndPaymentFlow(2000.DOLLARS, ref, alice.party, false, mockNet.defaultNotaryIdentity))
        alice.services.startFlow(CashIssueAndPaymentFlow(4000.DOLLARS, ref, bob.party, false, mockNet.defaultNotaryIdentity))
        mockNet.runNetwork()

        // Rotate notary identity and update network parameters.
        val newNotaryIdentity = DevIdentityGenerator.installKeyStoreWithNodeIdentity(
                mockNet.baseDirectory(mockNet.nextNodeId),
                DUMMY_NOTARY_NAME
        )
        val newNetworkParameters = testNetworkParameters(epoch = 2)
                .addNotary(mockNet.defaultNotaryIdentity, validating)
                .addNotary(newNotaryIdentity, validating)
        val ca = createDevNetworkMapCa()
        NetworkParametersCopier(newNetworkParameters, ca, overwriteFile = true).apply {
            install(mockNet.baseDirectory(alice))
            install(mockNet.baseDirectory(bob))
            install(mockNet.baseDirectory(mockNet.nextNodeId))
            install(mockNet.baseDirectory(mockNet.nextNodeId + 1).apply { createDirectories() })
        }

        // Start notary with new identity and restart nodes.
        val notary2 = mockNet.createNode(InternalMockNodeParameters(
                legalName = DUMMY_NOTARY_NAME,
                configOverrides = { doReturn(NotaryConfig(validating)).whenever(it).notary }
        ))
        val alice2 = mockNet.restartNode(alice)
        val bob2 = mockNet.restartNode(bob)
        val charlie = mockNet.createPartyNode(CHARLIE_NAME)

        // Save previous network parameters for subsequent backchain verification, because not persistent in mock network
        alice2.internals.services.networkParametersService.saveParameters(ca.sign(mockNet.networkParameters))
        bob2.internals.services.networkParametersService.saveParameters(ca.sign(mockNet.networkParameters))

        // Verify that notary identity has been changed.
        assertEquals(listOf(newNotaryIdentity), alice2.services.networkMapCache.notaryIdentities)
        assertEquals(listOf(newNotaryIdentity), bob2.services.networkMapCache.notaryIdentities)
        assertEquals(listOf(newNotaryIdentity), charlie.services.networkMapCache.notaryIdentities)

        assertEquals(newNotaryIdentity, alice2.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))
        assertEquals(newNotaryIdentity, bob2.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))
        assertEquals(newNotaryIdentity, charlie.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))

        assertEquals(newNotaryIdentity, alice2.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))
        assertEquals(newNotaryIdentity, bob2.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))
        assertEquals(newNotaryIdentity, charlie.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))

        // Move states notarized with previous notary identity.
        alice2.services.startFlow(CashPaymentFlow(3000.DOLLARS, bob2.party, false))
        mockNet.runNetwork()
        bob2.services.startFlow(CashPaymentFlow(7000.DOLLARS, charlie.party, false))
        mockNet.runNetwork()
        charlie.services.startFlow(CashPaymentFlow(7000.DOLLARS, alice2.party, false))
        mockNet.runNetwork()

        // Combine states notarized with previous and present notary identities.
        bob2.services.startFlow(CashIssueAndPaymentFlow(300.DOLLARS, ref, alice2.party, false, notary2.party))
        mockNet.runNetwork()
        alice2.services.startFlow(CashPaymentFlow(7300.DOLLARS, charlie.party, false))
        mockNet.runNetwork()

        // Verify that the ledger contains expected states.
        assertEquals(0.DOLLARS, alice2.services.getCashBalance(USD))
        assertEquals(0.DOLLARS, bob2.services.getCashBalance(USD))
        assertEquals(7300.DOLLARS, charlie.services.getCashBalance(USD))
    }

    @Test(timeout = 300_000)
    fun `rotate notary identity and new node receives netparams and understands old notary`() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = FINANCE_CORDAPPS + enclosedCordapp(),
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME, validating)),
                initialNetworkParameters = testNetworkParameters()
        )
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)

        // Issue states and notarize them with initial notary identity.
        alice.services.startFlow(CashIssueFlow(1000.DOLLARS, ref, mockNet.defaultNotaryIdentity))
        alice.services.startFlow(CashIssueAndPaymentFlow(2000.DOLLARS, ref, alice.party, false, mockNet.defaultNotaryIdentity))
        alice.services.startFlow(CashIssueAndPaymentFlow(4000.DOLLARS, ref, bob.party, false, mockNet.defaultNotaryIdentity))
        mockNet.runNetwork()

        val oldHash = alice.services.networkParametersService.currentHash

        // Rotate notary identity and update network parameters.
        val newNotaryIdentity = DevIdentityGenerator.installKeyStoreWithNodeIdentity(
                mockNet.baseDirectory(mockNet.nextNodeId),
                DUMMY_NOTARY_NAME
        )
        val newNetworkParameters = testNetworkParameters(epoch = 2)
                .addNotary(mockNet.defaultNotaryIdentity, validating)
                .addNotary(newNotaryIdentity, validating)
        val ca = createDevNetworkMapCa()
        NetworkParametersCopier(newNetworkParameters, ca, overwriteFile = true).apply {
            install(mockNet.baseDirectory(alice))
            install(mockNet.baseDirectory(bob))
            install(mockNet.baseDirectory(mockNet.nextNodeId))
            install(mockNet.baseDirectory(mockNet.nextNodeId + 1).apply { createDirectories() })
        }

        // Start notary with new identity and restart nodes.
        val notary2 = mockNet.createNode(InternalMockNodeParameters(
                legalName = DUMMY_NOTARY_NAME,
                configOverrides = { doReturn(NotaryConfig(validating)).whenever(it).notary }
        ))
        val alice2 = mockNet.restartNode(alice)
        val bob2 = mockNet.restartNode(bob)
        // We hide the old notary as trying to simulate it's replacement
        mockNet.hideNode(mockNet.defaultNotaryNode)
        val charlie = mockNet.createPartyNode(CHARLIE_NAME)

        // Save previous network parameters for subsequent backchain verification, because not persistent in mock network
        alice2.internals.services.networkParametersService.saveParameters(ca.sign(mockNet.networkParameters))
        bob2.internals.services.networkParametersService.saveParameters(ca.sign(mockNet.networkParameters))

        assertNotNull(alice2.services.networkParametersService.lookup(oldHash))
        assertNotNull(bob2.services.networkParametersService.lookup(oldHash))
        assertNull(charlie.services.networkParametersService.lookup(oldHash))

        // Verify that notary identity has been changed.
        assertEquals(listOf(newNotaryIdentity), alice2.services.networkMapCache.notaryIdentities)
        assertEquals(listOf(newNotaryIdentity), bob2.services.networkMapCache.notaryIdentities)
        assertEquals(listOf(newNotaryIdentity), charlie.services.networkMapCache.notaryIdentities)

        assertEquals(newNotaryIdentity, alice2.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))
        assertEquals(newNotaryIdentity, bob2.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))
        assertEquals(newNotaryIdentity, charlie.services.identityService.wellKnownPartyFromX500Name(DUMMY_NOTARY_NAME))

        assertEquals(newNotaryIdentity, alice2.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))
        assertEquals(newNotaryIdentity, bob2.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))
        assertEquals(newNotaryIdentity, charlie.services.identityService.wellKnownPartyFromAnonymous(mockNet.defaultNotaryIdentity))

        // Now send an existing transaction on Bob (from before rotation) to Charlie
        val bobVault: Vault.Page<Cash.State> = bob2.services.vaultService.queryBy(generateCashCriteria(USD))
        assertEquals(1, bobVault.states.size)
        val handle = bob2.services.startFlow(RpcSendTransactionFlow(bobVault.states[0].ref.txhash, charlie.party))
        mockNet.runNetwork()
        // Check flow completed successfully
        assertEquals(handle.resultFuture.getOrThrow(), Unit)

        // Check Charlie recorded it in the vault (could resolve notary, for example)
        val charlieVault: Vault.Page<Cash.State> = charlie.services.vaultService.queryBy(generateCashCriteria(USD))
        assertEquals(1, charlieVault.states.size)

        // Check Charlie gained the network parameters from before the rotation
        assertNotNull(charlie.services.networkParametersService.lookup(oldHash))

        // We unhide the old notary so it can be shutdown
        mockNet.unhideNode(mockNet.defaultNotaryNode)
    }

    private fun generateCashCriteria(currency: Currency): QueryCriteria {
        val stateCriteria = QueryCriteria.FungibleAssetQueryCriteria()
        val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(currency.currencyCode) }
        // This query should only return cash states the calling node is a participant of (meaning they can be modified/spent).
        val ccyCriteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex, relevancyStatus = Vault.RelevancyStatus.ALL)
        return stateCriteria.and(ccyCriteria)
    }

    @StartableByRPC
    @InitiatingFlow
    class RpcSendTransactionFlow(private val tx: SecureHash, private val party: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            val stx: SignedTransaction = serviceHub.validatedTransactions.getTransaction(tx)!!
            subFlow(SendTransactionFlow(session, stx))
        }
    }

    @InitiatedBy(RpcSendTransactionFlow::class)
    class RpcSendTransactionResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(otherSide, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }
}