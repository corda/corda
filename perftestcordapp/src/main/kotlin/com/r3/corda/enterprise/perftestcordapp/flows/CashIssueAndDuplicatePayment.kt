package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.FINALISING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.SIGNING_TX
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.util.*

abstract class AbstractCashIssueAndDuplicatePaymentFlow(
        val amount: Amount<Currency>,
        val issueRef: OpaqueBytes,
        val notary: Party,
        anonymous: Boolean,
        recipient: Party,
        progressTracker: ProgressTracker
) : AbstractConfidentialAwareCashFlow<StateAndRef<Cash.State>>(anonymous, recipient, progressTracker) {

    protected constructor(creator: AbstractCashIssueAndDuplicatePaymentFlow) : this(creator.amount, creator.issueRef, creator.notary, creator.anonymous, creator.recipient, creator.progressTracker)

    override fun makeAnonymousFlow(): AbstractConfidentialAwareCashFlow<StateAndRef<Cash.State>> {
        return CashIssueAndDuplicatePaymentAnonymous(this)
    }

    @Suspendable
    override fun mainCall(maybeAnonymousRecipient: AbstractParty, recipientSession: FlowSession): AbstractCashFlow.Result {
        fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty) = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

        val issueResult = subFlow(CashIssueFlow(amount, issueRef, notary))
        val cashStateAndRef: StateAndRef<Cash.State> = uncheckedCast(serviceHub.loadStates(setOf(StateRef(issueResult.id, 0))).single())

        val changeIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)

        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary)
        val (spendTx, keysForSigning) = OnLedgerAsset.generateSpend(builder, listOf(PartyAndAmount(maybeAnonymousRecipient, amount)), listOf(cashStateAndRef),
                changeIdentity.party.anonymise(),
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTx, keysForSigning)

        progressTracker.currentStep = FINALISING_TX
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        finaliseTx(tx, sessionsForFinality, "Unable to notarise spend first time")
        val notarised2 = finaliseTx(tx, sessionsForFinality, "Unable to notarise spend second time")

        return Result(notarised2.id, recipient)
    }
}

abstract class AbstractCashIssueAndDuplicatePaymentResponderFlow(anonymous: Boolean, otherSide: FlowSession) : AbstractConfidentialAwareCashResponderFlow<Unit>(anonymous, otherSide) {
    @Suspendable
    override fun respond() {
        // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}

/**
 * Initiates a flow that self-issues cash and then immediately spends it without coin selection.  It also then attempts
 * to notarise exactly the same transaction again, which should succeed since it is exactly the same notarisation request.
 *
 * @param amount the amount of currency to issue.
 * @param issueRef a reference to put on the issued currency.
 * @param recipient payee Party
 * @param anonymous whether to anonymise before the transaction
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
@InitiatingFlow
class CashIssueAndDuplicatePayment(amount: Amount<Currency>,
                                   issueRef: OpaqueBytes,
                                   recipient: Party,
                                   anonymous: Boolean,
                                   notary: Party,
                                   progressTracker: ProgressTracker) : AbstractCashIssueAndDuplicatePaymentFlow(amount, issueRef, notary, anonymous, recipient, progressTracker) {
    constructor(request: CashIssueAndPaymentFlow.IssueAndPaymentRequest) : this(request.amount, request.issueRef, request.recipient, request.anonymous, request.notary, tracker())
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, payTo: Party, anonymous: Boolean, notary: Party) : this(amount, issueRef, payTo, anonymous, notary, tracker())
}

@InitiatedBy(CashIssueAndDuplicatePayment::class)
class CashIssueAndDuplicatePaymentResponderFlow(otherSide: FlowSession) : AbstractCashIssueAndDuplicatePaymentResponderFlow(false, otherSide)

@InitiatingFlow
class CashIssueAndDuplicatePaymentAnonymous(creator: AbstractCashIssueAndDuplicatePaymentFlow) : AbstractCashIssueAndDuplicatePaymentFlow(creator)

@InitiatedBy(CashIssueAndDuplicatePaymentAnonymous::class)
class CashIssueAndDuplicatePaymentAnonymousResponderFlow(otherSide: FlowSession) : AbstractCashIssueAndDuplicatePaymentResponderFlow(true, otherSide)

