package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.FINALISING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_ID
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.SIGNING_TX
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.*

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
class CashIssueAndDuplicatePayment(val amount: Amount<Currency>,
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
        val cashStateAndRef: StateAndRef<Cash.State> = uncheckedCast(serviceHub.loadStates(setOf(StateRef(issueResult.id, 0))).single())

        progressTracker.currentStep = GENERATING_ID
        val recipientSession = initiateFlow(recipient)
        recipientSession.send(anonymous)
        val anonymousRecipient = if (anonymous) {
            subFlow(SwapIdentitiesFlow(recipientSession)).theirIdentity
        } else {
            recipient
        }

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
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        finaliseTx(tx, sessionsForFinality, "Unable to notarise spend first time")
        val notarised2 = finaliseTx(tx, sessionsForFinality, "Unable to notarise spend second time")

        return Result(notarised2.id, recipient)
    }
}

@InitiatedBy(CashIssueAndDuplicatePayment::class)
class CashIssueAndDuplicatePaymentResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val anonymous = otherSide.receive<Boolean>().unwrap { it }
        if (anonymous) {
            subFlow(SwapIdentitiesFlow(otherSide))
        }
        // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}
