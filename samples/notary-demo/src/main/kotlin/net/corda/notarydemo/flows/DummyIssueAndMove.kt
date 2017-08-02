package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.GBP
import net.corda.core.contracts.Issued
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Self issue an asset
        val amount = Amount(1000000, Issued(serviceHub.myInfo.legalIdentity.ref(0), GBP))
        val issueTxBuilder = TransactionBuilder(notary = notary)
        val signers = Cash().generateIssue(issueTxBuilder, amount, serviceHub.myInfo.legalIdentity, notary)
        val issueTx = serviceHub.signInitialTransaction(issueTxBuilder, signers)
        serviceHub.recordTransactions(issueTx)
        // Move ownership of the asset to the counterparty
        val counterPartyKey = counterpartyNode.owningKey
        val asset = issueTx.tx.outRef<Cash.State>(0)
        val moveTxBuilder = TransactionBuilder(notary = notary)

        val (_, keys) = serviceHub.vaultService.generateSpend(moveTxBuilder, Amount(amount.quantity, GBP), counterpartyNode)
        // We don't check signatures because we know that the notary's signature is missing
        return serviceHub.signInitialTransaction(moveTxBuilder, keys)
    }
}
