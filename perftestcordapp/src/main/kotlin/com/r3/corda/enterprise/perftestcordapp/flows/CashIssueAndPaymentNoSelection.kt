package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.*
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
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
 * @param issueRef a reference to put on the issued currency.
 * @param recipient payee Party
 * @param anonymous whether to anonymise before the transaction
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
class CashIssueAndPaymentNoSelection(val amount: Amount<Currency>,
                                     val issueRef: OpaqueBytes,
                                     val recipient: Party,
                                     val anonymous: Boolean,
                                     val notary: Party,
                                     progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(request: CashIssueAndPaymentFlow.IssueAndPaymentRequest) : this(request.amount, request.issueRef, request.recipient, request.anonymous, request.notary, tracker())
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, payTo: Party, anonymous: Boolean, notary: Party) : this(amount, issueRef, payTo, anonymous, notary, tracker())

    @Suspendable
    override fun call(): Result {
        fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty)
                = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

        val issueResult = subFlow(CashIssueFlow(amount, issueRef, notary))
        val cashStateAndRef = issueResult.stx.tx.outRef<Cash.State>(0)

        progressTracker.currentStep = GENERATING_ID
        val txIdentities = if (anonymous) {
            subFlow(SwapIdentitiesFlow(recipient))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val anonymousRecipient = txIdentities[recipient] ?: recipient

        val changeIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)

        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary)
        val (spendTx, keysForSigning) = OnLedgerAsset.generateSpend(builder, listOf(PartyAndAmount(anonymousRecipient, amount)), listOf(cashStateAndRef),
                changeIdentity.party.anonymise(),
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTx, keysForSigning)

        progressTracker.currentStep = FINALISING_TX
        val notarised = finaliseTx(tx, setOf(recipient), "Unable to notarise spend")
        return Result(notarised, recipient)
    }
}
