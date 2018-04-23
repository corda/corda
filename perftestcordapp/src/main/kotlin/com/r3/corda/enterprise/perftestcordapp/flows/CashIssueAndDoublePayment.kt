/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.OnLedgerAsset
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PartyAndAmount
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.*
import net.corda.core.flows.FlowException
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that self-issues cash.  We then try and send it to another party twice.  The flow only succeeds if
 * the second payment is rejected by the notary as a double spend.
 *
 * @param amount the amount of currency to issue.
 * @param issueRef a reference to put on the issued currency.
 * @param recipient payee Party
 * @param anonymous whether to anonymise before the transaction
 * @param notary the notary to set on the output states.
 */
@StartableByRPC
class CashIssueAndDoublePayment(val amount: Amount<Currency>,
                                val issueRef: OpaqueBytes,
                                val recipient: Party,
                                val anonymous: Boolean,
                                val notary: Party,
                                progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(request: CashIssueAndPaymentFlow.IssueAndPaymentRequest) : this(request.amount, request.issueRef, request.recipient, request.anonymous, request.notary, tracker())
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, payTo: Party, anonymous: Boolean, notary: Party) : this(amount, issueRef, payTo, anonymous, notary, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty)
                = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

        val issueResult = subFlow(CashIssueFlow(amount, issueRef, notary))
        val cashStateAndRef = serviceHub.loadStates(setOf(StateRef(issueResult.id, 0))).single() as StateAndRef<Cash.State>

        progressTracker.currentStep = GENERATING_ID
        val txIdentities = if (anonymous) {
            subFlow(SwapIdentitiesFlow(recipient))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val anonymousRecipient = txIdentities[recipient] ?: recipient

        val changeIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)

        progressTracker.currentStep = GENERATING_TX
        val builder1 = TransactionBuilder(notary)
        val (spendTx1, keysForSigning1) = OnLedgerAsset.generateSpend(builder1, listOf(PartyAndAmount(anonymousRecipient, amount)), listOf(cashStateAndRef),
                changeIdentity.party.anonymise(),
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        val builder2 = TransactionBuilder(notary)
        val (spendTx2, keysForSigning2) = OnLedgerAsset.generateSpend(builder2, listOf(PartyAndAmount(anonymousRecipient, amount)), listOf(cashStateAndRef),
                changeIdentity.party.anonymise(),
                { state, quantity, owner -> deriveState(state, quantity, owner) },
                { Cash().generateMoveCommand() })

        progressTracker.currentStep = SIGNING_TX
        val tx1 = serviceHub.signInitialTransaction(spendTx1, keysForSigning1)
        val tx2 = serviceHub.signInitialTransaction(spendTx2, keysForSigning2)

        progressTracker.currentStep = FINALISING_TX
        val notarised1 = finaliseTx(tx1, setOf(recipient), "Unable to notarise spend first time")
        try {
            val notarised2 = finaliseTx(tx2, setOf(recipient), "Unable to notarise spend second time")
        } catch (expected: CashException) {
            val cause = expected.cause
            if (cause is NotaryException) {
                if (cause.error is NotaryError.Conflict) {
                    return Result(notarised1.id, recipient)
                }
                throw expected // Wasn't actually expected!
            }
        }
        throw FlowException("Managed to do double spend.  Should have thrown NotaryError.Conflict.")
    }
}