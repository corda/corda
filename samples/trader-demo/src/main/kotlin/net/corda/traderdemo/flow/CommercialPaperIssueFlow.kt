/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.days
import net.corda.core.utilities.seconds
import net.corda.finance.`issued by`
import net.corda.finance.contracts.CommercialPaper
import java.time.Instant
import java.util.*

/**
 * Flow for the Bank of Corda node to issue some commercial paper to the seller's node, to sell to the buyer.
 */
@StartableByRPC
class CommercialPaperIssueFlow(private val amount: Amount<Currency>,
                               private val issueRef: OpaqueBytes,
                               private val recipient: Party,
                               private val notary: Party,
                               override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, recipient: Party, notary: Party) : this(amount, issueRef, recipient, notary, tracker())

    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

        object ISSUING : ProgressTracker.Step("Issuing and timestamping some commercial paper")

        fun tracker() = ProgressTracker(ISSUING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = ISSUING

        val issuance: SignedTransaction = run {
            val tx = CommercialPaper().generateIssue(ourIdentity.ref(issueRef), amount `issued by` ourIdentity.ref(issueRef),
                    Instant.now() + 10.days, notary)

            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

            // Requesting a time-window to be set, all CP must have a validation window.
            tx.setTimeWindow(Instant.now(), 30.seconds)

            // Sign it as ourselves.
            val stx = serviceHub.signInitialTransaction(tx)

            subFlow(FinalityFlow(stx))
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.

        return run {
            val builder = TransactionBuilder(notary)
            CommercialPaper().generateMove(builder, issuance.tx.outRef(0), recipient)
            val stx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(stx))
        }
    }
}
