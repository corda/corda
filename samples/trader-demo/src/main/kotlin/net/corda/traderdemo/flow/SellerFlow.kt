package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.CommercialPaper
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.days
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.FinalityFlow
import net.corda.flows.NotaryFlow
import net.corda.flows.TwoPartyTradeFlow
import net.corda.testing.BOC
import java.time.Instant
import java.util.*

@InitiatingFlow
@StartableByRPC
class SellerFlow(val otherParty: Party,
                 val amount: Amount<Currency>,
                 override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(otherParty: Party, amount: Amount<Currency>) : this(otherParty, amount, tracker())

    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

        object SELF_ISSUING : ProgressTracker.Step("Got session ID back, issuing and timestamping some commercial paper")

        object TRADING : ProgressTracker.Step("Starting the trade flow") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyTradeFlow.Seller.tracker()
        }

        // We vend a progress tracker that already knows there's going to be a TwoPartyTradingFlow involved at some
        // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
        // surprised when it appears as a new set of tasks below the current one.
        fun tracker() = ProgressTracker(SELF_ISSUING, TRADING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SELF_ISSUING

        val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
        val cpOwnerKey = serviceHub.keyManagementService.freshKey()
        val commercialPaper = selfIssueSomeCommercialPaper(serviceHub.myInfo.legalIdentity, notary)

        progressTracker.currentStep = TRADING

        // Send the offered amount.
        send(otherParty, amount)
        val seller = TwoPartyTradeFlow.Seller(
                otherParty,
                notary,
                commercialPaper,
                amount,
                AnonymousParty(cpOwnerKey),
                progressTracker.getChildProgressTracker(TRADING)!!)
        return subFlow(seller)
    }

    @Suspendable
    fun selfIssueSomeCommercialPaper(ownedBy: AbstractParty, notaryNode: NodeInfo): StateAndRef<CommercialPaper.State> {
        // Make a fake company that's issued its own paper.
        val party = Party(BOC.name, serviceHub.legalIdentityKey)

        val issuance: SignedTransaction = run {
            val tx = CommercialPaper().generateIssue(party.ref(1, 2, 3), 1100.DOLLARS `issued by` DUMMY_CASH_ISSUER,
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
            val builder = TransactionType.General.Builder(notaryNode.notaryIdentity)
            CommercialPaper().generateMove(builder, issuance.tx.outRef(0), ownedBy)
            val stx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(stx)).single()
        }

        return move.tx.outRef(0)
    }

}
