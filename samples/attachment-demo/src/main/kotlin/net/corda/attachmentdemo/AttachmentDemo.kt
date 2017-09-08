package net.corda.attachmentdemo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


@StartableByRPC
class AttachmentDemoFlow(val otherSide: Party, val notary: Party, val hash: SecureHash.SHA256) : FlowLogic<SignedTransaction>() {

    object SIGNING : ProgressTracker.Step("Signing transaction")

    override val progressTracker: ProgressTracker = ProgressTracker(SIGNING)

    @Suspendable
    override fun call(): SignedTransaction {
        // Create a trivial transaction with an output that describes the attachment, and the attachment itself
        val ptx = TransactionBuilder(notary)
                .addOutputState(AttachmentContract.State(hash))
                .addCommand(AttachmentContract.Command, serviceHub.legalIdentityKey)
                .addAttachment(hash)

        progressTracker.currentStep = SIGNING

        // Send the transaction to the other recipient
        val stx = serviceHub.signInitialTransaction(ptx)

        return subFlow(FinalityFlow(stx, setOf(otherSide))).single()
    }
}


class AttachmentContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val state = tx.outputsOfType<AttachmentContract.State>().single()
        val attachment = tx.attachments.single()
        require(state.hash == attachment.id)
    }

    object Command : TypeOnlyCommandData()

    data class State(val hash: SecureHash.SHA256) : ContractState {
        override val contract: Contract = AttachmentContract()
        override val participants: List<AbstractParty> = emptyList()
    }
}
