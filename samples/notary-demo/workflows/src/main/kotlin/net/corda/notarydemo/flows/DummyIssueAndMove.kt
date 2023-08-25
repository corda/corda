package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.notarydemo.contracts.DO_NOTHING_PROGRAM_ID
import net.corda.notarydemo.contracts.DummyCommand
import net.corda.notarydemo.contracts.DummyState

@InitiatingFlow
@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party, private val discriminator: Int) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Self issue an asset
        val state = DummyState(listOf(ourIdentity), discriminator)
        val issueTx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addOutputState(state, DO_NOTHING_PROGRAM_ID)
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
        serviceHub.recordTransactions(issueTx)

        val stx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addInputState(issueTx.tx.outRef<ContractState>(0))
            addOutputState(state.copy(participants = listOf(counterpartyNode)), DO_NOTHING_PROGRAM_ID)
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })

        return subFlow(FinalityFlow(stx, initiateFlow(counterpartyNode)))
    }
}

@InitiatedBy(DummyIssueAndMove::class)
class DummyIssueAndMoveResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // As a non-participant to the transaction we need to record all states
        subFlow(ReceiveFinalityFlow(otherSide, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}