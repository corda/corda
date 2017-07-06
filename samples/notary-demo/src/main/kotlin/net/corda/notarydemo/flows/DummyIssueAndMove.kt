package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.testing.contracts.DummyContract
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import java.util.*

@StartableByRPC
class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val random = Random()
        // Self issue an asset
        val issueTxBuilder = DummyContract.generateInitial(random.nextInt(), notary, serviceHub.myInfo.legalIdentity.ref(0))
        val issueTx = serviceHub.signInitialTransaction(issueTxBuilder)
        serviceHub.recordTransactions(issueTx)
        // Move ownership of the asset to the counterparty
        val counterPartyKey = counterpartyNode.owningKey
        val asset = issueTx.tx.outRef<DummyContract.SingleOwnerState>(0)
        val moveTxBuilder = DummyContract.move(asset, counterpartyNode)
        val moveTx = serviceHub.signInitialTransaction(moveTxBuilder)
        // We don't check signatures because we know that the notary's signature is missing
        return moveTx
    }
}
