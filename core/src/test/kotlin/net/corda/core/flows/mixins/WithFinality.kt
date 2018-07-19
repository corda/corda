package net.corda.core.flows.mixins

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.StartedNode
import net.corda.testing.core.singleIdentity

interface WithFinality : WithMockNet {

    //region Operations
    fun StartedNode<*>.finalise(stx: SignedTransaction, vararg additionalParties: Party) =
        startFlowAndRunNetwork(FinalityFlow(stx, additionalParties.toSet()))

    fun StartedNode<*>.getValidatedTransaction(stx: SignedTransaction) =
        services.validatedTransactions.getTransaction(stx.id)!!

    fun CordaRPCOps.finalise(stx: SignedTransaction, vararg parties: Party) =
        startFlow(::FinalityInvoker, stx, parties.toSet())
            .andRunNetwork()
    //endregion

    //region Matchers
    fun transactionVisibleTo(other: StartedNode<*>) = object : Matcher<SignedTransaction> {
        override val description = "has a transaction visible to ${other.info.singleIdentity()}"
        override fun invoke(actual: SignedTransaction) =
                equalTo(actual)(other.getValidatedTransaction(actual))
    }
    //endregion

    @StartableByRPC
    class FinalityInvoker(private val transaction: SignedTransaction,
                          private val extraRecipients: Set<Party>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction = subFlow(FinalityFlow(transaction, extraRecipients))
    }
}