package net.corda.docs.tutorial.helloworld

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

// DOCSTART 01
// Add these imports:
import net.corda.core.contracts.Command
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

// Replace TemplateFlow's definition with:
@InitiatingFlow
@StartableByRPC
class IOUFlow(val iouValue: Int,
              val otherParty: Party) : FlowLogic<Unit>() {

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val cmd = Command(TemplateContract.Commands.Action(), ourIdentity.owningKey)

        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, TEMPLATE_CONTRACT_ID)
                .addCommand(cmd)

        // We sign the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // We finalise the transaction.
        subFlow(FinalityFlow(signedTx))
    }
}
// DOCEND 01