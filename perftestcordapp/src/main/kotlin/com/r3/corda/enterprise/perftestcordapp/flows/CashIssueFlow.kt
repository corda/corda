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
import com.r3.corda.enterprise.perftestcordapp.issuedBy
import net.corda.core.contracts.Amount
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
class CashIssueFlow(private val amount: Amount<Currency>,
                    private val issuerBankPartyRef: OpaqueBytes,
                    private val notary: Party,
                    progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
    constructor(amount: Amount<Currency>,
                issuerBankPartyRef: OpaqueBytes,
                notary: Party) : this(amount, issuerBankPartyRef, notary, tracker())
    constructor(request: IssueRequest) : this(request.amount, request.issueRef, request.notary, tracker())

    @Suspendable
    override fun call(): AbstractCashFlow.Result {
        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary)
        val issuer = ourIdentity.ref(issuerBankPartyRef)
        val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), ourIdentity, notary)
        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING_TX
        // There is no one to send the tx to as we're the only participants
        val notarised = finaliseTx(tx, emptySet(), "Unable to notarise issue")
        return Result(notarised.id, ourIdentity)
    }

    @CordaSerializable
    class IssueRequest(amount: Amount<Currency>, val issueRef: OpaqueBytes, val notary: Party) : AbstractRequest(amount)
}
