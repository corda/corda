package net.corda.flows

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.currency
import net.corda.core.flows.FlowStateMachine
import net.corda.core.map
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuerFlowTest {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var bankOfCordaNode: MockNetwork.MockNode
    lateinit var bankClientNode: MockNetwork.MockNode

    @Test
    fun `test issuer flow`() {
        net = MockNetwork(false, true)
        ledger {
            notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
            bankOfCordaNode = net.createPartyNode(notaryNode.info.address, BOC.name, BOC_KEY)
            bankClientNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name, MEGA_CORP_KEY)

            // using default IssueTo Party Reference
            val issueToPartyAndRef = MEGA_CORP.ref(OpaqueBytes.Companion.of(123))
            val (issuer, issuerResult) = runIssuerAndIssueRequester(1000000.DOLLARS, issueToPartyAndRef)
            assertEquals(issuerResult.get(), issuer.get().resultFuture.get())

            // try to issue an amount of a restricted currency
            assertFailsWith<Exception> {
                runIssuerAndIssueRequester(Amount(100000L, currency("BRL")), issueToPartyAndRef).issueRequestResult.get()
            }

            bankOfCordaNode.stop()
            bankClientNode.stop()

            bankOfCordaNode.manuallyCloseDB()
            bankClientNode.manuallyCloseDB()
        }
    }

    private fun runIssuerAndIssueRequester(amount: Amount<Currency>, issueToPartyAndRef: PartyAndReference) : RunResult {
        val issuerFuture = bankOfCordaNode.initiateSingleShotFlow(IssuerFlow.IssuanceRequester::class) {
            otherParty -> IssuerFlow.Issuer(issueToPartyAndRef.party)
        }.map { it.stateMachine }

        val issueRequest = IssuanceRequester(amount, issueToPartyAndRef.party, issueToPartyAndRef.reference, bankOfCordaNode.info.legalIdentity)
        val issueRequestResultFuture = bankClientNode.services.startFlow(issueRequest).resultFuture

        return IssuerFlowTest.RunResult(issuerFuture, issueRequestResultFuture)
    }

    private data class RunResult(
            val issuer: ListenableFuture<FlowStateMachine<*>>,
            val issueRequestResult: ListenableFuture<SignedTransaction>
    )
}