package net.corda.verification

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap


@StartableByRPC
@InitiatingFlow
class TestCommsFlowInitiator(val x500Name: CordaX500Name? = null) : FlowLogic<List<String>>() {

    object SENDING : ProgressTracker.Step("SENDING")
    object RECIEVED_ALL : ProgressTracker.Step("RECIEVED_ALL")
    object FINALIZING : ProgressTracker.Step("FINALIZING")

    override val progressTracker: ProgressTracker = ProgressTracker(SENDING, RECIEVED_ALL, FINALIZING)

    @Suspendable
    override fun call(): List<String> {
        progressTracker.currentStep = SENDING
        val responses = serviceHub.networkMapCache.allNodes.map {
            it.legalIdentities.first()
        }.filterNot {
            it in serviceHub.myInfo.legalIdentities
        }.filterNot {
            it in serviceHub.networkMapCache.notaryIdentities
        }.filter(::matchesX500)
                .map {
                    val initiateFlow = initiateFlow(it)
                    initiateFlow.receive<String>().unwrap { it }
                }.toList().also {
                    progressTracker.currentStep = RECIEVED_ALL
                }
        val tx = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        tx.addOutputState(CommsTestState(responses, serviceHub.myInfo.legalIdentities.first()), CommsTestContract::class.qualifiedName!!)
        tx.addCommand(CommsTestCommand, serviceHub.myInfo.legalIdentities.first().owningKey)
        val signedTx = serviceHub.signInitialTransaction(tx)
        subFlow(FinalityFlow(signedTx))
        return responses
    }

    fun matchesX500(it: Party): Boolean {
        return x500Name?.equals(it.name) ?: true
    }
}

@InitiatedBy(TestCommsFlowInitiator::class)
class TestCommsFlowResponder(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherSideSession.send("Hello from: " + serviceHub.myInfo.legalIdentities.first().name.toString())
    }

}

@CordaSerializable
data class CommsTestState(val responses: List<String>, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)

}


@CordaSerializable
object CommsTestCommand : CommandData


class CommsTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}