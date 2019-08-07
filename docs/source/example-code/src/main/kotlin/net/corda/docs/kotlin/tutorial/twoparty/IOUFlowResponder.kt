@file:Suppress("unused")

package net.corda.docs.kotlin.tutorial.twoparty

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.docs.kotlin.tutorial.helloworld.IOUFlow
import net.corda.docs.kotlin.tutorial.helloworld.IOUState

// Add these imports:
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.SignedTransaction

// Define IOUFlowResponder:
@InitiatedBy(IOUFlow::class)
class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    // DOCSTART 1
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)
            }
        }
        val expectedTxId = subFlow(signTransactionFlow).id
        subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId))
    }
    // DOCEND 1
}
