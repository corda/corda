package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party, private val discriminator: Int) : FlowLogic<SignedTransaction>() {
    object DoNothingContract : Contract {
        override val legalContractReference = byteArrayOf().sha256()
        override fun verify(tx: LedgerTransaction) {}
    }

    data class State(override val participants: List<AbstractParty>, private val discriminator: Int) : ContractState {
        override val contract = DoNothingContract
    }

    @Suspendable
    override fun call() = serviceHub.run {
        // Self issue an asset
        val state = State(listOf(myInfo.legalIdentity), discriminator)
        val issueTx = signInitialTransaction(TransactionBuilder(notary).apply {
            addOutputState(state)
        })
        recordTransactions(issueTx)
        // Move ownership of the asset to the counterparty
        // We don't check signatures because we know that the notary's signature is missing
        signInitialTransaction(TransactionBuilder(notary).apply {
            addInputState(issueTx.tx.outRef<ContractState>(0))
            addOutputState(state.copy(participants = listOf(counterpartyNode)))
        })
    }
}
