package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.TestStartedNode

interface WithFinality : WithMockNet {
    //region Operations
    fun TestStartedNode.finalise(stx: SignedTransaction, vararg recipients: Party): FlowStateMachineHandle<SignedTransaction> {
        return startFlowAndRunNetwork(FinalityInvoker(stx, recipients.toSet(), emptySet()))
    }

    fun TestStartedNode.getValidatedTransaction(stx: SignedTransaction): SignedTransaction = services.getRequiredTransaction(stx.id)

    fun CordaRPCOps.finalise(stx: SignedTransaction, vararg recipients: Party): FlowHandle<SignedTransaction> {
        return startFlow(WithFinality::FinalityInvoker, stx, recipients.toSet(), emptySet()).andRunNetwork()
    }
    //endregion

    //region Matchers
    fun visibleTo(other: TestStartedNode) = object : Matcher<SignedTransaction> {
        override val description = "has a transaction visible to ${other.info.singleIdentity()}"
        override fun invoke(actual: SignedTransaction): MatchResult {
            return equalTo(actual)(other.getValidatedTransaction(actual))
        }
    }
    //endregion

    @InitiatingFlow
    @StartableByRPC
    class FinalityInvoker(private val transaction: SignedTransaction,
                          private val newRecipients: Set<Party>,
                          private val oldRecipients: Set<Party>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val sessions = newRecipients.map(::initiateFlow)
            @Suppress("DEPRECATION")
            return subFlow(FinalityFlow(transaction, sessions, oldRecipients, FinalityFlow.tracker()))
        }
    }

    @InitiatedBy(FinalityInvoker::class)
    class FinalityResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    @StartableByRPC
    class OldFinalityInvoker(private val transaction: SignedTransaction) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            @Suppress("DEPRECATION")
            return subFlow(FinalityFlow(transaction))
        }
    }
}
