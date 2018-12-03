package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.FINALISING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.SIGNING_TX
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

abstract class AbstractCashPaymentFromKnownStatesFlow(
        val inputs: Set<StateRef>,
        val numberOfStates: Int,
        val numberOfChangeStates: Int,
        val amountPerState: Amount<Currency>,
        recipient: Party,
        anonymous: Boolean,
        progressTracker: ProgressTracker) : AbstractConfidentialAwareCashFlow<Unit>(anonymous, recipient, progressTracker) {

    // Used by anonymous code path.
    constructor(creator: AbstractCashPaymentFromKnownStatesFlow) : this(creator.inputs, creator.numberOfStates, creator.numberOfChangeStates, creator.amountPerState, creator.recipient, creator.anonymous, creator.progressTracker)

    override fun makeAnonymousFlow(): AbstractCashPaymentFromKnownStatesFlow = CashPaymentFromKnownStatesAnonymousFlow(this)

    @Suspendable
    override fun mainCall(maybeAnonymousRecipient: AbstractParty, recipientSession: FlowSession): AbstractCashFlow.Result {
        fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty) = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary = null)

        // TODO: this needs to use a bulk call
        val cashStateAndRef = inputs.map { serviceHub.toStateAndRef<Cash.State>(it) }
        val amounts = ArrayList(Collections.nCopies(numberOfStates, PartyAndAmount(maybeAnonymousRecipient, amountPerState)))
        val changeParty = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false).party.anonymise()
        if (numberOfChangeStates > 1) {
            amounts += Collections.nCopies(numberOfChangeStates - 1, PartyAndAmount(changeParty, amountPerState))
        }
        val (spendTx, keysForSigning) = OnLedgerAsset.generateSpend(builder, amounts, cashStateAndRef,
                changeParty,
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTx, keysForSigning)

        progressTracker.currentStep = FINALISING_TX
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        val notarised = finaliseTx(tx, sessionsForFinality, "Unable to notarise spend")
        return AbstractCashFlow.Result(notarised.id, maybeAnonymousRecipient)
    }
}

abstract class AbstractCashPaymentFromKnownStatesResponderFlow(anonymous: Boolean, otherSide: FlowSession) : AbstractConfidentialAwareCashResponderFlow<Unit>(anonymous, otherSide) {
    @Suspendable
    override fun respond() {
        // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}

@StartableByRPC
@InitiatingFlow
class CashPaymentFromKnownStatesFlow(
        inputs: Set<StateRef>,
        numberOfStates: Int,
        numberOfChangeStates: Int,
        amountPerState: Amount<Currency>,
        recipient: Party,
        anonymous: Boolean,
        progressTracker: ProgressTracker
) : AbstractCashPaymentFromKnownStatesFlow(inputs, numberOfStates, numberOfChangeStates, amountPerState, recipient, anonymous, progressTracker) {

    constructor(inputs: Set<StateRef>,
                numberOfStates: Int,
                numberOfChangeStates: Int,
                amountPerState: Amount<Currency>,
                recipient: Party,
                anonymous: Boolean
    ) : this(inputs, numberOfStates, numberOfChangeStates, amountPerState, recipient, anonymous, tracker())
}

@InitiatedBy(CashPaymentFromKnownStatesFlow::class)
class CashPaymentFromKnownStatesResponderFlow(otherSide: FlowSession) : AbstractCashPaymentFromKnownStatesResponderFlow(false, otherSide)

@InitiatingFlow
class CashPaymentFromKnownStatesAnonymousFlow(creator: AbstractCashPaymentFromKnownStatesFlow) : AbstractCashPaymentFromKnownStatesFlow(creator)

@InitiatedBy(CashPaymentFromKnownStatesAnonymousFlow::class)
class CashPaymentFromKnownStatesResponderAnonymousFlow(otherSide: FlowSession) : AbstractCashPaymentFromKnownStatesResponderFlow(true, otherSide)

