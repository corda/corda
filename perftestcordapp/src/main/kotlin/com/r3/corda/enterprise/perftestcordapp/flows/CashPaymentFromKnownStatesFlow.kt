package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

@StartableByRPC
class CashPaymentFromKnownStatesFlow(
        val inputs: Set<StateRef>,
        val numberOfStates: Int,
        val numberOfChangeStates: Int,
        val amountPerState: Amount<Currency>,
        val recipient: Party,
        val anonymous: Boolean,
        progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {

    constructor(inputs: Set<StateRef>,
                numberOfStates: Int,
                numberOfChangeStates: Int,
                amountPerState: Amount<Currency>,
                recipient: Party,
                anonymous: Boolean) : this(inputs, numberOfStates, numberOfChangeStates, amountPerState, recipient, anonymous, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty) = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

        progressTracker.currentStep = AbstractCashFlow.Companion.GENERATING_ID
        val txIdentities = if (anonymous) {
            subFlow(SwapIdentitiesFlow(recipient))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val anonymousRecipient = txIdentities[recipient] ?: recipient
        progressTracker.currentStep = AbstractCashFlow.Companion.GENERATING_TX
        val builder = TransactionBuilder(notary = null)

        // TODO: this needs to use a bulk call
        val cashStateAndRef = inputs.map { serviceHub.toStateAndRef<Cash.State>(it) }
        val amounts = ArrayList(Collections.nCopies(numberOfStates, PartyAndAmount(anonymousRecipient, amountPerState)))
        val changeParty = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false).party.anonymise()
        if (numberOfChangeStates > 1) {
            amounts += Collections.nCopies(numberOfChangeStates - 1, PartyAndAmount(changeParty, amountPerState))
        }
        val (spendTx, keysForSigning) = OnLedgerAsset.generateSpend(builder, amounts, cashStateAndRef,
                changeParty,
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        progressTracker.currentStep = AbstractCashFlow.Companion.SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTx, keysForSigning)

        progressTracker.currentStep = AbstractCashFlow.Companion.FINALISING_TX
        val notarised = finaliseTx(tx, setOf(recipient), "Unable to notarise spend")
        return AbstractCashFlow.Result(notarised.id, anonymousRecipient)
    }
}