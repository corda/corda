package net.corda.docs.tutorial.helloworld

// DOCSTART 01
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import kotlin.reflect.jvm.jvmName

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

        // We create a transaction builder
        val txBuilder = TransactionBuilder(notary = notary)

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val outputContract = IOUContract::class.jvmName
        val outputContractAndState = StateAndContract(outputState, outputContract)
        val cmd = Command(IOUContract.Create(), ourIdentity.owningKey)

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Finalising the transaction.
        subFlow(FinalityFlow(signedTx))
    }
}
// DOCEND 01