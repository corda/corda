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
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.internal.Emoji
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.TwoPartyTradeFlow
import net.corda.traderdemo.TransactionGraphSearch
import java.util.*

@InitiatedBy(SellerFlow::class)
class BuyerFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

    object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")

    override val progressTracker: ProgressTracker = ProgressTracker(STARTING_BUY)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = STARTING_BUY

        // Receive the offered amount and automatically agree to it (in reality this would be a longer negotiation)
        val amount = otherSideSession.receive<Amount<Currency>>().unwrap { it }
        require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
        val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
        val buyer = TwoPartyTradeFlow.Buyer(
                otherSideSession,
                notary,
                amount,
                CommercialPaper.State::class.java)

        // This invokes the trading flow and out pops our finished transaction.
        val tradeTX: SignedTransaction = subFlow(buyer)

        println("Purchase complete - we are a happy customer! Final transaction is: " +
                "\n\n${Emoji.renderIfSupported(tradeTX.tx)}")

        logIssuanceAttachment(tradeTX)
        logBalance()
    }

    private fun logBalance() {
        val balances = serviceHub.getCashBalances().entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Remaining balance: ${balances.joinToString()}")
    }

    private fun logIssuanceAttachment(tradeTX: SignedTransaction) {
        // Find the original CP issuance.
        // TODO: This is potentially very expensive, and requires transaction details we may no longer have once
        //       SGX is enabled. Should be replaced with including the attachment on all transactions involving
        //       the state.
        val search = TransactionGraphSearch(serviceHub.validatedTransactions, listOf(tradeTX.tx),
                TransactionGraphSearch.Query(withCommandOfType = CommercialPaper.Commands.Issue::class.java,
                        followInputsOfType = CommercialPaper.State::class.java))
        val cpIssuance = search.call().single()

        // Buyer will fetch the attachment from the seller automatically when it resolves the transaction.

        cpIssuance.attachments.first().let {
            println("""

The issuance of the commercial paper came with an attachment. You can find it in the attachments directory: $it.jar

${Emoji.renderIfSupported(cpIssuance)}""")
        }
    }
}
