package net.corda.finance.flows

import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toHexString
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.*
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashPaymentFlowTests {
    private lateinit var mockNet: MockNetwork
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: StartedMockNode
    private lateinit var bankOfCorda: Party
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode

    private lateinit var issuanceTx: SignedTransaction

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(servicePeerAllocationStrategy = RoundRobin(), cordappsForAllNodes = FINANCE_CORDAPPS))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        val future = bankOfCordaNode.startFlow(CashIssueFlow(initialBalance, ref, mockNet.defaultNotaryIdentity))
        issuanceTx = future.getOrThrow().stx
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `pay some cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        // Register for vault updates
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultService.trackBy<Cash.State>(criteria)
        val (_, vaultUpdatesBankClient) = aliceNode.services.vaultService.trackBy<Cash.State>(criteria)

        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expectedPayment, payTo))
        mockNet.runNetwork()
        val payTx = future.getOrThrow().stx

        // pay Bob (not anonymously as we want to check that Bob owns it)
        val futureBob = aliceNode.startFlow(CashPaymentFlow(expectedPayment, bobNode.info.singleIdentity(), false))
        mockNet.runNetwork()
        val bobTx = futureBob.getOrThrow().stx

        // Check Bank of Corda vault updates - we take in some issued cash and split it into $500 to the notary
        // and $1,500 back to us, so we expect to consume one state, produce one state for our own vault
        vaultUpdatesBoc.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).hasSize(1)
                assertThat(produced).hasSize(1)
                val changeState = produced.single().state.data
                assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
            }
        }

        // Check notary node vault updates
        vaultUpdatesBankClient.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).isEmpty()
                assertThat(produced).hasSize(1)
                val paymentState = produced.single().state.data
                assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
            }
        }


        listOf(bobNode, aliceNode, bankOfCordaNode).forEach { node ->
            listOf(issuanceTx, payTx, bobTx).forEach {  stx ->
                println("${node.info.singleIdentity()} UNENCRYPTED: ${node.services.validatedTransactions.getTransaction(stx.id)}")
                println("${node.info.singleIdentity()} ENCRYPTED:   ${node.services.validatedTransactions.getEncryptedTransaction(stx.id)?.let { "${stx.id} -> ${it.bytes.toHexString()}"}}")
            }
        }

        bobNode.services.vaultService.queryBy(Cash.State::class.java).states.forEach {
            println("BOB: ${it.state.data}")
        }
    }

    @Test(timeout=300_000)
	fun `pay more than we have`() {
        val payTo = aliceNode.info.singleIdentity()
        val expected = 4000.DOLLARS
        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expected,
                payTo))
        mockNet.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }

    @Test(timeout=300_000)
	fun `pay zero cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expected,
                payTo))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
