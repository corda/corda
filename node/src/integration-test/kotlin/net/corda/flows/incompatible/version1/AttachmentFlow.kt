package net.corda.flows.incompatible.version1

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.incompatible.version1.ATTACHMENT_PROGRAM_ID
import net.corda.contracts.incompatible.version1.AttachmentContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class AttachmentFlow(private val otherSide: Party,
                     private val notary: Party,
                     private val attachId: SecureHash.SHA256,
                     private val notariseInputState: StateAndRef<AttachmentContract.State>? = null) : FlowLogic<SignedTransaction>() {

    object SIGNING : ProgressTracker.Step("Signing transaction")

    override val progressTracker: ProgressTracker = ProgressTracker(SIGNING)

    @Suspendable
    override fun call(): SignedTransaction {
        val session = initiateFlow(otherSide)
        val notarise = notariseInputState != null
        session.send(notarise)  // inform peer whether to sign for notarisation

        // Create a trivial transaction with an output that describes the attachment, and the attachment itself
        val ptx = TransactionBuilder(notary)
                .addOutputState(AttachmentContract.State(attachId), ATTACHMENT_PROGRAM_ID)
                .addAttachment(attachId)
        if (notarise) {
            ptx.addInputState(notariseInputState!!)
            ptx.addCommand(AttachmentContract.Command, ourIdentity.owningKey, otherSide.owningKey)
        }
        else
            ptx.addCommand(AttachmentContract.Command, ourIdentity.owningKey)

        progressTracker.currentStep = SIGNING

        val stx = serviceHub.signInitialTransaction(ptx)
        val ftx = if (notarise) {
            subFlow(CollectSignaturesFlow(stx, listOf(session)))
        } else stx

        return subFlow(FinalityFlow(ftx, setOf(session), statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}

@InitiatedBy(AttachmentFlow::class)
class StoreAttachmentFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val notarise = otherSide.receive<Boolean>().unwrap { it }
        if (notarise) {
            val stx = subFlow(object : SignTransactionFlow(otherSide) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
            subFlow(ReceiveFinalityFlow(otherSide, stx.id, statesToRecord = StatesToRecord.ALL_VISIBLE))
        } else {
            subFlow(ReceiveFinalityFlow(otherSide, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }
}

@StartableByRPC
class AttachmentIssueFlow(private val attachId: SecureHash.SHA256,
                          private val notary: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val builder = TransactionBuilder(notary)
        builder.addAttachment(attachId)
        builder.addOutputState(TransactionState(AttachmentContract.State(attachId), ATTACHMENT_PROGRAM_ID, notary))
        builder.addCommand(Command(AttachmentContract.Command, listOf(ourIdentity.owningKey)))
        val tx = serviceHub.signInitialTransaction(builder, ourIdentity.owningKey)
        return subFlow(FinalityFlow(tx, emptySet<FlowSession>(), statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}
