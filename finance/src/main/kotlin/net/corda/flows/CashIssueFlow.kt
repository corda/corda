package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.finance.issuedBy
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that self-issues cash (which should then be sent to recipient(s) using a payment transaction).
 *
 * We issue cash only to ourselves so that all KYC/AML checks on payments are enforced consistently, rather than risk
 * checks for issuance and payments differing. Outside of test scenarios it would be extremely unusual to issue cash
 * and immediately transfer it, so impact of this limitation is considered minimal.
 *
 * @param amount the amount of currency to issue.
 * @param issuerBankPartyRef a reference to put on the issued currency.
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
class CashIssueFlow(val amount: Amount<Currency>,
                    val issuerBankPartyRef: OpaqueBytes,
                    val notary: Party,
                    progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(amount: Amount<Currency>,
                issuerBankPartyRef: OpaqueBytes,
                notary: Party) : this(amount, issuerBankPartyRef, notary, tracker())
    constructor(request: IssueRequest) : this(request.amount, request.issueRef, request.notary, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        val issuerCert = serviceHub.myInfo.legalIdentityAndCert

        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary)
        val issuer = issuerCert.party.ref(issuerBankPartyRef)
        val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), issuerCert.party, notary)
        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING_TX
        subFlow(FinalityFlow(tx))
        return Result(tx, issuerCert.party)
    }

    @CordaSerializable
    class IssueRequest(amount: Amount<Currency>, val issueRef: OpaqueBytes, val notary: Party) : AbstractRequest(amount)
}
