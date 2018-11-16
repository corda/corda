package net.corda.core.flows.mixins

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.TestStartedNode

interface WithFinality : WithMockNet {
    //region Operations
    fun TestStartedNode.finalise(stx: SignedTransaction, vararg recipients: Party): FlowStateMachine<SignedTransaction> {
        return startFlowAndRunNetwork(FinalityInvoker(stx, recipients.toSet()))
    }

    fun TestStartedNode.getValidatedTransaction(stx: SignedTransaction): SignedTransaction {
        return services.validatedTransactions.getTransaction(stx.id)!!
    }

    fun CordaRPCOps.finalise(stx: SignedTransaction, vararg recipients: Party): FlowHandle<SignedTransaction> {
        return startFlow(::FinalityInvoker, stx, recipients.toSet()).andRunNetwork()
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
                          private val recipients: Set<Party>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val sessions = recipients.map(::initiateFlow)
            return subFlow(FinalityFlow(transaction, sessions))
        }
    }

    @InitiatedBy(FinalityInvoker::class)
    class FinalityResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}
