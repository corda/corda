package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.CommercialPaper
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.`issued by`
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.days
import net.corda.core.utilities.seconds
import java.time.Instant
import java.util.*

/**
 * Flow for the Bank of Corda node to issue some commercial paper to the seller's node, to sell to the buyer.
 */
@InitiatingFlow
@StartableByRPC
class BankFlow(val otherParty: Party,
                 val amount: Amount<Currency>,
                 override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(otherParty: Party, amount: Amount<Currency>) : this(otherParty, amount, tracker())

    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")
        object ISSUING : ProgressTracker.Step("Issuing and timestamping some commercial paper")
        fun tracker() = ProgressTracker(ISSUING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = ISSUING

        val notaryNode: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
        val issuance: SignedTransaction = run {
            // TODO: Replace dummy cash issuer with Bank of Corda
            val tx = CommercialPaper().generateIssue(serviceHub.myInfo.legalIdentity.ref(1, 2, 3), amount `issued by` DUMMY_CASH_ISSUER,
                    Instant.now() + 10.days, notaryNode.notaryIdentity)

            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

            // Requesting a time-window to be set, all CP must have a validation window.
            tx.setTimeWindow(Instant.now(), 30.seconds)

            // Sign it as ourselves.
            val stx = serviceHub.signInitialTransaction(tx)

            subFlow(FinalityFlow(stx)).single()
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.
        val move: SignedTransaction = run {
            val builder = TransactionBuilder(notaryNode.notaryIdentity)
            CommercialPaper().generateMove(builder, issuance.tx.outRef(0), otherParty)
            val stx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(stx)).single()
        }

        return move
    }

}
