package net.corda.sample.businessnetwork.iou

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.sample.businessnetwork.membership.flow.CheckMembershipFlow
import net.corda.sample.businessnetwork.membership.flow.CheckMembershipResult
import kotlin.reflect.jvm.jvmName

@InitiatingFlow
@StartableByRPC
class IOUFlow(val iouValue: Int,
              val otherParty: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        // TODO: Derive membership name from CorDapp config.
        val allowedMembershipName =
                CordaX500Name("AliceBobMembershipList", "Oslo", "NO")
    }

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call(): SignedTransaction {
        // Prior to creating any state, check on our side whether [otherParty] belongs to desired membership list.
        // Also obtain consent from [otherParty] to borrow from us.
        // This is done early enough in the flow such that if the other party rejects - do not do any unnecessary processing in this flow.
        // Even if this is not done, later on upon signatures collection phase membership will be checked on the other side and
        // transaction rejected if this doesn't hold. See [IOUFlowResponder] for more information.
        check(subFlow(CheckMembershipFlow(otherParty, allowedMembershipName)) == CheckMembershipResult.PASS)

        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create a transaction builder
        val txBuilder = TransactionBuilder(notary = notary)

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val outputContract = IOUContract::class.jvmName
        val outputContractAndState = StateAndContract(outputState, outputContract)
        val cmd = Command(IOUContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party.
        val otherpartySession = initiateFlow(otherParty)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherpartySession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx))
    }
}