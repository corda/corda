package net.corda.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.issuedBy
import net.corda.core.crypto.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
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
class CashIssueFlow(val amount: Amount<Currency>,
                    val issueRef: OpaqueBytes,
                    val recipient: Party,
                    val notary: Party,
                    progressTracker: ProgressTracker) : AbstractCashFlow(progressTracker) {
    constructor(amount: Amount<Currency>,
                issueRef: OpaqueBytes,
                recipient: Party,
                notary: Party) : this(amount, issueRef, recipient, notary, tracker())

    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATING_TX
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = null)
        val issuer = serviceHub.myInfo.legalIdentity.ref(issueRef)
        Cash().generateIssue(builder, amount.issuedBy(issuer), recipient.owningKey, notary)
        progressTracker.currentStep = SIGNING_TX
        val myKey = serviceHub.legalIdentityKey
        builder.signWith(myKey)
        val tx = builder.toSignedTransaction()
        progressTracker.currentStep = FINALISING_TX
        subFlow(FinalityFlow(tx))
        return tx
    }
}
