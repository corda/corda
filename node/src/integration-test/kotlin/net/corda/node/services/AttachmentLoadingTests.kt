package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.workflows.asset.CashUtils
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.Currency

class AttachmentLoadingTests {
    @Test(timeout=300_000)
	fun `contracts downloaded from the network are not executed`() {
        driver(DriverParameters(
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                cordappsForAllNodes = listOf(enclosedCordapp())
        )) {
            val (alice, bob) = listOf(
                    startNode(NodeParameters(ALICE_NAME, additionalCordapps = FINANCE_CORDAPPS)),
                    startNode(NodeParameters(BOB_NAME, additionalCordapps = listOf(FINANCE_WORKFLOWS_CORDAPP)))
            ).transpose().getOrThrow()

            alice.rpc.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0x00), defaultNotaryIdentity).returnValue.getOrThrow()

            assertThatThrownBy { alice.rpc.startFlow(::ConsumeAndBroadcastFlow, 10.DOLLARS, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow() }
                    // ConsumeAndBroadcastResponderFlow re-throws any non-FlowExceptions with just their class name in the message so that
                    // we can verify here Bob threw the correct exception
                    .hasMessage(TransactionVerificationException.UntrustedAttachmentsException::class.java.name)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class ConsumeAndBroadcastFlow(private val amount: Amount<Currency>, private val otherSide: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val builder = TransactionBuilder(notary)
            val (_, keysForSigning) = CashUtils.generateSpend(
                    serviceHub,
                    builder,
                    amount,
                    ourIdentityAndCert,
                    otherSide,
                    anonymous = false
            )
            val stx = serviceHub.signInitialTransaction(builder, keysForSigning)
            val session = initiateFlow(otherSide)
            subFlow(FinalityFlow(stx, session))
            // It's important we wait on this dummy receive, as otherwise it's possible we miss any errors the other side throws
            session.receive<String>().unwrap { require(it == "OK") { "Not OK: $it"} }
        }
    }

    @InitiatedBy(ConsumeAndBroadcastFlow::class)
    class ConsumeAndBroadcastResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            try {
                subFlow(ReceiveFinalityFlow(otherSide))
            } catch (e: FlowException) {
                throw e
            } catch (e: Exception) {
                throw FlowException(e.javaClass.name)
            }
            otherSide.send("OK")
        }
    }
}
