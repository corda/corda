package net.corda.configsample

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


@StartableByRPC
class TestNotaryFlow : FlowLogic<String>() {

    object BUILDING : ProgressTracker.Step("BUILDING")
    object REQUESTING_NOTARY : ProgressTracker.Step("REQUESTING_NOTARY")
    object FINALIZED : ProgressTracker.Step("FINALIZED")

    override val progressTracker: ProgressTracker = ProgressTracker(BUILDING, REQUESTING_NOTARY, FINALIZED)


    override fun call(): String {
        val transactionBuilder = TransactionBuilder()
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        transactionBuilder.notary = notary;
        transactionBuilder.addOutputState(NotaryTestState(notary.name.toString(), serviceHub.myInfo.legalIdentities.first()), NotaryTestContract::class.qualifiedName!!)
        transactionBuilder.addCommand(NotaryTestCommand, serviceHub.myInfo.legalIdentities.first().owningKey)
        val signedTx = serviceHub.signInitialTransaction(transactionBuilder)
        progressTracker.currentStep = REQUESTING_NOTARY
        val result = subFlow(FinalityFlow(signedTx))
        progressTracker.currentStep = FINALIZED
        return "notarised: " + result.notary.toString() + "::" + result.tx.id
    }
}


@CordaSerializable
data class NotaryTestState(val id: String, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)

}


@CordaSerializable
object NotaryTestCommand : CommandData


class NotaryTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}