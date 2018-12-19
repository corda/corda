@file:Suppress("DEPRECATION", "unused", "UNUSED_PARAMETER")

package net.corda.docs.kotlin

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

private fun dummyTransactionWithParticipant(party: Party): SignedTransaction = TODO()

// DOCSTART SimpleFlowUsingOldApi
class SimpleFlowUsingOldApi(private val counterparty: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val stx = dummyTransactionWithParticipant(counterparty)
        return subFlow(FinalityFlow(stx))
    }
}
// DOCEND SimpleFlowUsingOldApi

// DOCSTART SimpleFlowUsingNewApi
// Notice how the flow *must* now be an initiating flow even when it wasn't before.
@InitiatingFlow
class SimpleFlowUsingNewApi(private val counterparty: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val stx = dummyTransactionWithParticipant(counterparty)
        // For each non-local participant in the transaction we must initiate a flow session with them.
        val session = initiateFlow(counterparty)
        return subFlow(FinalityFlow(stx, session))
    }
}
// DOCEND SimpleFlowUsingNewApi

// DOCSTART SimpleNewResponderFlow
// All participants will run this flow to receive and record the finalised transaction into their vault.
@InitiatedBy(SimpleFlowUsingNewApi::class)
class SimpleNewResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSide))
    }
}
// DOCEND SimpleNewResponderFlow

// DOCSTART ExistingInitiatingFlow
// Assuming the previous version of the flow was 1 (the default if none is specified), we increment the version number to 2
// to allow for backwards compatibility with nodes running the old CorDapp.
@InitiatingFlow(version = 2)
class ExistingInitiatingFlow(private val counterparty: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val partiallySignedTx = dummyTransactionWithParticipant(counterparty)
        val session = initiateFlow(counterparty)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(session)))
        // Determine which version of the flow that other side is using.
        return if (session.getCounterpartyFlowInfo().flowVersion == 1) {
            // Use the old API if the other side is using the previous version of the flow.
            subFlow(FinalityFlow(fullySignedTx))
        } else {
            // Otherwise they're at least on version 2 and so we can send the finalised transaction on the existing session.
            subFlow(FinalityFlow(fullySignedTx, session))
        }
    }
}
// DOCEND ExistingInitiatingFlow

@InitiatedBy(ExistingInitiatingFlow::class)
class ExistingResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val txWeJustSigned = subFlow(object : SignTransactionFlow(otherSide) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
                // Do checks here
            }
        })
        // DOCSTART ExistingResponderFlow
        if (otherSide.getCounterpartyFlowInfo().flowVersion >= 2) {
            // The other side is not using the old CorDapp so call ReceiveFinalityFlow to record the finalised transaction.
            // If SignTransactionFlow is used then we can verify the tranaction we receive for recording is the same one
            // that was just signed.
            subFlow(ReceiveFinalityFlow(otherSide, expectedTxId = txWeJustSigned.id))
        } else {
            // Otherwise the other side is running the old CorDapp and so we don't need to do anything further. The node
            // will automatically record the finalised transaction using the old insecure mechanism.
        }
        // DOCEND ExistingResponderFlow
    }
}
