package net.corda.verification

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

    object ISSUING : ProgressTracker.Step("ISSUING")
    object ISSUED : ProgressTracker.Step("ISSUED")
    object DESTROYING : ProgressTracker.Step("DESTROYING")
    object FINALIZED : ProgressTracker.Step("FINALIZED")

    override val progressTracker: ProgressTracker = ProgressTracker(ISSUING, ISSUED, DESTROYING, FINALIZED)


    override fun call(): String {
        val issueBuilder = TransactionBuilder()
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        issueBuilder.notary = notary;
        val myIdentity = serviceHub.myInfo.legalIdentities.first()
        issueBuilder.addOutputState(NotaryTestState(notary.name.toString(), myIdentity), NotaryTestContract::class.qualifiedName!!)
        issueBuilder.addCommand(NotaryTestCommand, myIdentity.owningKey)
        val signedTx = serviceHub.signInitialTransaction(issueBuilder)
        val issueResult = subFlow(FinalityFlow(signedTx))
        progressTracker.currentStep = ISSUED
        val destroyBuilder = TransactionBuilder()
        destroyBuilder.notary = notary;
        destroyBuilder.addInputState(issueResult.tx.outRefsOfType<NotaryTestState>().first())
        destroyBuilder.addCommand(NotaryTestCommand, myIdentity.owningKey)
        val signedDestroyT = serviceHub.signInitialTransaction(destroyBuilder)
        val result = subFlow(FinalityFlow(signedDestroyT))
        progressTracker.currentStep = DESTROYING
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