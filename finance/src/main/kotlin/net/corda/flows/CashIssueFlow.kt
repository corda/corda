package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.issuedBy
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
 * @param issueRef a reference to put on the issued currency.
 * @param recipient the party who should own the currency after it is issued.
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
class CashIssueFlow(val amount: Amount<Currency>,
                    val issueRef: OpaqueBytes,
                    val recipient: Party,
                    val notary: Party,
                    val anonymous: Boolean,
                    progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(amount: Amount<Currency>,
                issueRef: OpaqueBytes,
                recipient: Party,
                notary: Party) : this(amount, issueRef, recipient, notary, true, tracker())
    constructor(amount: Amount<Currency>,
                issueRef: OpaqueBytes,
                recipient: Party,
                notary: Party,
                anonymous: Boolean) : this(amount, issueRef, recipient, notary, anonymous, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        progressTracker.currentStep = GENERATING_ID
        val txIdentities = if (anonymous) {
            subFlow(TransactionKeyFlow(recipient))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val anonymousRecipient = txIdentities[recipient] ?: recipient
        progressTracker.currentStep = GENERATING_TX
        val builder: TransactionBuilder = TransactionBuilder(notary)
        val issuer = serviceHub.myInfo.legalIdentity.ref(issueRef)
        val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), anonymousRecipient, notary)
        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING_TX
        subFlow(FinalityFlow(tx))
        return Result(tx, anonymousRecipient)
    }
}
