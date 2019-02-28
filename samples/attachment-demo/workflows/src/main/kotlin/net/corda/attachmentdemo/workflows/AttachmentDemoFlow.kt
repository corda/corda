package net.corda.attachmentdemo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.attachmentdemo.contracts.ATTACHMENT_PROGRAM_ID
import net.corda.attachmentdemo.contracts.AttachmentContract
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class AttachmentDemoFlow(private val otherSide: Party,
                         private val notary: Party,
                         private val attachId: SecureHash.SHA256) : FlowLogic<SignedTransaction>() {

    object SIGNING : ProgressTracker.Step("Signing transaction")

    override val progressTracker: ProgressTracker = ProgressTracker(SIGNING)

    @Suspendable
    override fun call(): SignedTransaction {
        // Create a trivial transaction with an output that describes the attachment, and the attachment itself
        val ptx = TransactionBuilder(notary)
                .addOutputState(AttachmentContract.State(attachId), ATTACHMENT_PROGRAM_ID)
                .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
                .addAttachment(attachId)

        progressTracker.currentStep = SIGNING

        val stx = serviceHub.signInitialTransaction(ptx)

        // Send the transaction to the other recipient
        return subFlow(FinalityFlow(stx, initiateFlow(otherSide)))
    }
}

@InitiatedBy(AttachmentDemoFlow::class)
class StoreAttachmentFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // As a non-participant to the transaction we need to record all states
        subFlow(ReceiveFinalityFlow(otherSide, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}

@StartableByRPC
@StartableByService
class NoProgressTrackerShellDemo : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        return "You Called me!"
    }
}
