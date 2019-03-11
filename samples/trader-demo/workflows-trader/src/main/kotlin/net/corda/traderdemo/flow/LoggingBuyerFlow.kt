package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.internal.Emoji
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.workflows.getCashBalances

@InitiatedBy(SellerFlow::class)
class LoggingBuyerFlow(private val otherSideSession: FlowSession) : BuyerFlow(otherSideSession) {

    @Suspendable
    override fun call(): SignedTransaction {
        val tradeTX = super.call()
        logIssuanceAttachment(tradeTX)
        logBalance()
        return tradeTX
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

The issuance of the commercial paper came with an attachment with hash $it.

${Emoji.renderIfSupported(cpIssuance)}""")
        }
    }
}
