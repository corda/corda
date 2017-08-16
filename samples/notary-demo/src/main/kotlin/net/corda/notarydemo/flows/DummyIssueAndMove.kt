package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.GBP

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
        val amount = Amount(1000000, Issued(myInfo.legalIdentity.ref(0), GBP))
        val issueTxBuilder = TransactionBuilder(notary = notary)
        val signers = Cash().generateIssue(issueTxBuilder, amount, serviceHub.myInfo.legalIdentity, notary)
        val issueTx = serviceHub.signInitialTransaction(issueTxBuilder, signers)
        serviceHub.recordTransactions(issueTx)
        // Move ownership of the asset to the counterparty
        val moveTxBuilder = TransactionBuilder(notary = notary)

        val (_, keys) = Cash.generateSpend(serviceHub, moveTxBuilder, Amount(amount.quantity, GBP), counterpartyNode)
        // We don't check signatures because we know that the notary's signature is missing
        signInitialTransaction(moveTxBuilder, keys)
    }
}
