package net.corda.finance.test.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashException
import net.corda.finance.issuedBy
import java.util.Currency

@StartableByRPC
@InitiatingFlow
class CashIssueWithObserversFlow(private val amount: Amount<Currency>,
                                 private val issuerBankPartyRef: OpaqueBytes,
                                 private val notary: Party,
                                 private val observers: Set<Party>) : AbstractCashFlow<AbstractCashFlow.Result>(tracker()) {
    @Suspendable
    override fun call(): Result {
        progressTracker.currentStep = Companion.GENERATING_TX
        val builder = TransactionBuilder(notary)
        val issuer = ourIdentity.ref(issuerBankPartyRef)
        val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), ourIdentity, notary)
        progressTracker.currentStep = Companion.SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = Companion.FINALISING_TX
        val observerSessions = observers.map { initiateFlow(it) }
        val notarised = finalise(tx, observerSessions, "Unable to notarise issue")
        return Result(notarised, ourIdentity)
    }

    @Suspendable
    private fun finalise(tx: SignedTransaction, sessions: Collection<FlowSession>, message: String): SignedTransaction {
        try {
            return subFlow(FinalityFlow(tx, sessions))
        } catch (e: NotaryException) {
            throw CashException(message, e)
        }
    }
}

@InitiatedBy(CashIssueWithObserversFlow::class)
class CashIssueReceiverFlowWithObservers(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide, statesToRecord = StatesToRecord.ALL_VISIBLE))
        }
    }
}