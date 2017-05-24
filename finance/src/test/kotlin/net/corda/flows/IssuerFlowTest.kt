package net.corda.flows

import com.google.common.util.concurrent.ListenableFuture
import net.corda.contracts.testing.calculateRandomlySizedAmounts
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.currency
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowStateMachine
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.map
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.BOC
import net.corda.testing.MEGA_CORP
import net.corda.testing.initiateSingleShotFlow
import net.corda.testing.ledger
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuerFlowTest {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNode
    lateinit var bankOfCordaNode: MockNode
    lateinit var bankClientNode: MockNode

    @Test
    fun `test issuer flow`() {
        net = MockNetwork(false, true)
        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
            bankOfCordaNode = net.createPartyNode(notaryNode.info.address, BOC.name)
            bankClientNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name)

            // using default IssueTo Party Reference
            val (issuer, issuerResult) = runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, 1000000.DOLLARS,
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123))
            assertEquals(issuerResult.get(), issuer.get().resultFuture.get())

            // try to issue an amount of a restricted currency
            assertFailsWith<FlowException> {
                runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(100000L, currency("BRL")),
                        bankClientNode.info.legalIdentity, OpaqueBytes.of(123)).issueRequestResult.getOrThrow()
            }

            bankOfCordaNode.stop()
            bankClientNode.stop()
        }
    }

    @Test
    fun `test issue flow to self`() {
        net = MockNetwork(false, true)
        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
            bankOfCordaNode = net.createPartyNode(notaryNode.info.address, BOC.name)

            // using default IssueTo Party Reference
            val (issuer, issuerResult) = runIssuerAndIssueRequester(bankOfCordaNode, bankOfCordaNode, 1000000.DOLLARS,
                    bankOfCordaNode.info.legalIdentity, OpaqueBytes.of(123))
            assertEquals(issuerResult.get(), issuer.get().resultFuture.get())

            bankOfCordaNode.stop()
        }
    }

    @Test
    fun `test concurrent issuer flow`() {

        net = MockNetwork(false, true)
        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
            bankOfCordaNode = net.createPartyNode(notaryNode.info.address, BOC.name)
            bankClientNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name)

            // this test exercises the Cashflow issue and move subflows to ensure consistent spending of issued states
            val amount = 10000.DOLLARS
            val amounts = calculateRandomlySizedAmounts(10000.DOLLARS, 10, 10, Random())
            val handles = amounts.map { pennies ->
                runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(pennies, amount.token),
                        bankClientNode.info.legalIdentity, OpaqueBytes.of(123))
            }
            handles.forEach {
                require(it.issueRequestResult.get() is SignedTransaction)
            }

            bankOfCordaNode.stop()
            bankClientNode.stop()
        }
    }

    private fun runIssuerAndIssueRequester(issuerNode: MockNode, issueToNode: MockNode,
                                           amount: Amount<Currency>,
                                           party: Party, ref: OpaqueBytes): RunResult {
        val issueToPartyAndRef = party.ref(ref)
        val issuerFuture = issuerNode.initiateSingleShotFlow(IssuerFlow.IssuanceRequester::class) { _ ->
            IssuerFlow.Issuer(party)
        }.map { it.stateMachine }

        val issueRequest = IssuanceRequester(amount, party, issueToPartyAndRef.reference, issuerNode.info.legalIdentity)
        val issueRequestResultFuture = issueToNode.services.startFlow(issueRequest).resultFuture

        return IssuerFlowTest.RunResult(issuerFuture, issueRequestResultFuture)
    }

    private data class RunResult(
            val issuer: ListenableFuture<FlowStateMachine<*>>,
            val issueRequestResult: ListenableFuture<SignedTransaction>
    )
}