package net.corda.flows

import com.google.common.util.concurrent.ListenableFuture
import net.corda.testing.contracts.calculateRandomlySizedAmounts
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.currency
import net.corda.core.flows.FlowException
import net.corda.core.internal.FlowStateMachine
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.map
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.DUMMY_NOTARY
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.BOC
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuerFlowTest {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNode
    lateinit var bankOfCordaNode: MockNode
    lateinit var bankClientNode: MockNode

    @Before
    fun start() {
        mockNet = MockNetwork(threadPerNode = true)
        notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        bankOfCordaNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOC.name)
        bankClientNode = mockNet.createPartyNode(notaryNode.network.myAddress, MEGA_CORP.name)
        val nodes = listOf(notaryNode, bankOfCordaNode, bankClientNode)

        nodes.forEach { node ->
            nodes.map { it.info.legalIdentityAndCert }.forEach(node.services.identityService::registerIdentity)
        }
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `test issuer flow`() {
        // using default IssueTo Party Reference
        val (issuer, issuerResult) = runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, 1000000.DOLLARS,
                bankClientNode.info.legalIdentity, OpaqueBytes.of(123))
        assertEquals(issuerResult.get().stx, issuer.get().resultFuture.get())

        // try to issue an amount of a restricted currency
        assertFailsWith<FlowException> {
            runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(100000L, currency("BRL")),
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123)).issueRequestResult.getOrThrow()
        }
    }

    @Test
    fun `test issue flow to self`() {
        // using default IssueTo Party Reference
        val (issuer, issuerResult) = runIssuerAndIssueRequester(bankOfCordaNode, bankOfCordaNode, 1000000.DOLLARS,
                bankOfCordaNode.info.legalIdentity, OpaqueBytes.of(123))
        assertEquals(issuerResult.get().stx, issuer.get().resultFuture.get())
    }

    @Test
    fun `test concurrent issuer flow`() {
        // this test exercises the Cashflow issue and move subflows to ensure consistent spending of issued states
        val amount = 10000.DOLLARS
        val amounts = calculateRandomlySizedAmounts(10000.DOLLARS, 10, 10, Random())
        val handles = amounts.map { pennies ->
            runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(pennies, amount.token),
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123))
        }
        handles.forEach {
            require(it.issueRequestResult.get().stx is SignedTransaction)
        }
    }

    private fun runIssuerAndIssueRequester(issuerNode: MockNode,
                                           issueToNode: MockNode,
                                           amount: Amount<Currency>,
                                           party: Party,
                                           ref: OpaqueBytes): RunResult {
        val issueToPartyAndRef = party.ref(ref)
        val issuerFlows: Observable<IssuerFlow.Issuer> = issuerNode.registerInitiatedFlow(IssuerFlow.Issuer::class.java)
        val firstIssuerFiber = issuerFlows.toFuture().map { it.stateMachine }

        val issueRequest = IssuanceRequester(amount, party, issueToPartyAndRef.reference, issuerNode.info.legalIdentity,
                anonymous = false)
        val issueRequestResultFuture = issueToNode.services.startFlow(issueRequest).resultFuture

        return IssuerFlowTest.RunResult(firstIssuerFiber, issueRequestResultFuture)
    }

    private data class RunResult(
            val issuer: ListenableFuture<FlowStateMachine<*>>,
            val issueRequestResult: ListenableFuture<AbstractCashFlow.Result>
    )
}