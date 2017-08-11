package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.finance.issuedBy
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.TransactionKeyFlow
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that produces cash issuance transaction.
 *
 * @param amount the amount of currency to issue.
 * @param issuerBankPartyRef a reference to put on the issued currency.
 * @param issueTo the party who should own the currency after it is issued.
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
class CashIssueFlow(val amount: Amount<Currency>,
                    val issueTo: Party,
                    val issuerBankParty
                    : Party,
                    val issuerBankPartyRef: OpaqueBytes,
                    val notary: Party,
                    val anonymous: Boolean,
                    progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(amount: Amount<Currency>,
                issuerBankPartyRef: OpaqueBytes,
                issuerBankParty: Party,
                issueTo: Party,
                notary: Party) : this(amount, issueTo, issuerBankParty, issuerBankPartyRef, notary, true, tracker())
    constructor(amount: Amount<Currency>,
                issueTo: Party,
                issuerBankParty: Party,
                issuerBankPartyRef: OpaqueBytes,
                notary: Party,
                anonymous: Boolean) : this(amount, issueTo, issuerBankParty, issuerBankPartyRef, notary, anonymous, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        progressTracker.currentStep = GENERATING_ID
        val txIdentities = if (anonymous) {
            subFlow(TransactionKeyFlow(issueTo))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val anonymousRecipient = txIdentities[issueTo] ?: issueTo
        progressTracker.currentStep = GENERATING_TX
        val builder: TransactionBuilder = TransactionBuilder(notary)
        val issuer = issuerBankParty.ref(issuerBankPartyRef)
        val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), anonymousRecipient, notary)
        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING_TX
        subFlow(FinalityFlow(tx))
        return Result(tx, anonymousRecipient)
    }
}
