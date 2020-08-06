package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.demo.contracts.LoanContract
import net.corda.bn.demo.contracts.LoanState
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow issues [LoanState] with initiator as lender and borrower provided as flow argument. It also performs verification on both
 * parties to ensure they are active members of Business Network with [networkId] and that initiator has permission to issue the loan.
 *
 * @property networkId ID of the Business Network the issued loan will belong to.
 * @property borrower Identity of party to take loan.
 * @property amount Amount of the loan to be returned.
 */
@InitiatingFlow
@StartableByRPC
class IssueLoanFlow(private val networkId: String, private val borrower: Party, private val amount: Int) : BusinessNetworkIntegrationFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        businessNetworkFullVerification(networkId, ourIdentity, borrower)

        val outputState = LoanState(lender = ourIdentity, borrower = borrower, amount = amount, networkId = networkId)
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(outputState)
                .addCommand(LoanContract.Commands.Issue(), ourIdentity.owningKey, borrower.owningKey)
        builder.verify(serviceHub)

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val sessions = listOf(initiateFlow(borrower))
        val fullSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, sessions))
        return subFlow(FinalityFlow(fullSignedTransaction, sessions))
    }
}

@InitiatedBy(IssueLoanFlow::class)
class IssueLoanResponderFlow(private val session: FlowSession) : BusinessNetworkIntegrationFlow<Unit>() {

    @Suspendable
    override fun call() {
        val signResponder = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is LoanContract.Commands.Issue) {
                    throw FlowException("Only LoanContract.Commands.Issue command is allowed")
                }

                val loanState = stx.tx.outputStates.single() as LoanState
                loanState.apply {
                    if (lender != session.counterparty) {
                        throw FlowException("Lender doesn't match sender's identity")
                    }
                    if (borrower != ourIdentity) {
                        throw FlowException("Borrower doesn't match receiver's identity")
                    }
                    businessNetworkFullVerification(networkId, lender, borrower)
                }
            }
        }
        val stx = subFlow(signResponder)

        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}