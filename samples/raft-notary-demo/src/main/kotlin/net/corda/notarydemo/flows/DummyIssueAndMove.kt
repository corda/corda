package net.corda.notarydemo.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.DummyContract
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.recordTransactions
import net.corda.core.transactions.SignedTransaction
import java.util.*

class DummyIssueAndMove(private val notary: Party, private val counterpartyNode: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val random = Random()
        val myKeyPair = serviceHub.legalIdentityKey
        // Self issue an asset
        val issueTx = DummyContract.generateInitial(serviceHub.myInfo.legalIdentity.ref(0), random.nextInt(), notary).apply {
            signWith(myKeyPair)
        }
        serviceHub.recordTransactions(issueTx.toSignedTransaction())
        // Move ownership of the asset to the counterparty
        val counterPartyKey = counterpartyNode.owningKey
        val asset = issueTx.toWireTransaction().outRef<DummyContract.SingleOwnerState>(0)
        val moveTx = DummyContract.move(asset, counterPartyKey).apply {
            signWith(myKeyPair)
        }
            // We don't check signatures because we know that the notary's signature is missing
        return moveTx.toSignedTransaction(checkSufficientSignatures = false)
    }
}
