package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.notarydemo.contracts.DO_NOTHING_PROGRAM_ID
import net.corda.notarydemo.contracts.DummyCommand
import net.corda.notarydemo.contracts.DummyState

@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party, private val discriminator: Int) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Self issue an asset
        val states = (1..1000).map { DummyState(listOf(ourIdentity), it) }
        val issueTx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            for (state in states) {
                addOutputState(state, DO_NOTHING_PROGRAM_ID)
            }
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })
        serviceHub.recordTransactions(issueTx)

        val stx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addInputState(issueTx.tx.outRef<ContractState>(0))
            for (state in states) {
                addOutputState(state.copy(participants = listOf(counterpartyNode)), DO_NOTHING_PROGRAM_ID)
            }
            addCommand(DummyCommand(), listOf(ourIdentity.owningKey))
        })

        serviceHub.recordTransactions(stx)

        return stx
    }
}