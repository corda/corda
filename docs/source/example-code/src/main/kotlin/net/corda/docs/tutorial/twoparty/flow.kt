package net.corda.docs.tutorial.twoparty

import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.docs.tutorial.helloworld.IOUContract
import net.corda.docs.tutorial.helloworld.IOUState
import kotlin.reflect.jvm.jvmName

// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
object flow : FlowLogic<Unit>() {
    val iouValue = Any() as Int
    val otherParty = Any() as Party
    val txBuilder = Any() as TransactionBuilder
    override fun call() {
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE


        // DOCSTART 01
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
        subFlow(FinalityFlow(fullySignedTx))
        // DOCEND 01


// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
    }
}
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE