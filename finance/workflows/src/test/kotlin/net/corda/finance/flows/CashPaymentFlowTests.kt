package net.corda.finance.flows

import net.corda.core.crypto.Crypto
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

        listOf(bankOfCordaNode, aliceNode, bobNode).forEach { node ->
            println("------------------------")
            println("${node.info.singleIdentity()}")
            println("------------------------")
            listOf(
                    "Bank issue to self: " to issuanceTx,
                    "Bank pays Alice:    " to payTx,
                    "Alice pays Bob:     " to bobTx).forEach {  labelToStx ->
                val label = labelToStx.first
                val stx = labelToStx.second
                println("$label (${stx.id})")
                println("> FOUND UNENCRYPTED: ${node.services.validatedTransactions.getTransaction(stx.id)}")
                println("> FOUND   ENCRYPTED: ${node.services.validatedTransactions.getVerifiedEncryptedTransaction(stx.id)?.let { "${shortStringDesc(it.bytes.toHexString())} signature ${it.verifierSignature.toHexString()}"}}")

                println()
            }
            println()
        }

        bobNode.services.vaultService.queryBy(Cash.State::class.java).states.forEach {
            println("BOB: ${it.state.data}")
        }
    }

    private fun shortStringDesc(longString : String) : String {
        return "EncryptedTransaction(${longString.take(15)}...${longString.takeLast(15)})"
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
