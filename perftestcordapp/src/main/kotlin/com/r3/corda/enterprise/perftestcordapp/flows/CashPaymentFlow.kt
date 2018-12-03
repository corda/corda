package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.FINALISING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_TX
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.SIGNING_TX
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

abstract class AbstractCashPaymentFlow(
        val amount: Amount<Currency>,
        val issuerConstraint: Set<Party> = emptySet(),
        anonymous: Boolean,
        recipient: Party,
        progressTracker: ProgressTracker) : AbstractConfidentialAwareCashFlow<Unit>(anonymous, recipient, progressTracker) {

    protected constructor(creator: AbstractCashPaymentFlow) : this(creator.amount, creator.issuerConstraint, creator.anonymous, creator.recipient, creator.progressTracker)

    override fun makeAnonymousFlow(): AbstractConfidentialAwareCashFlow<Unit> {
        return CashPaymentAnonymousFlow(this)
    }

    @Suspendable
    override fun mainCall(maybeAnonymousRecipient: AbstractParty, recipientSession: FlowSession): AbstractCashFlow.Result {
        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary = null)
        // TODO: Have some way of restricting this to states the caller controls
        val (spendTX, keysForSigning) = try {
            Cash.generateSpend(serviceHub,
                    builder,
                    amount,
                    ourIdentityAndCert,
                    maybeAnonymousRecipient,
                    issuerConstraint)
        } catch (e: InsufficientBalanceException) {
            throw CashException("Insufficient cash for spend: ${e.message}", e)
        }

        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(spendTX, keysForSigning)

        progressTracker.currentStep = FINALISING_TX
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        val notarised = finaliseTx(tx, sessionsForFinality, "Unable to notarise spend")
        return Result(notarised.id, maybeAnonymousRecipient)
    }
}

abstract class AbstractCashPaymentResponderFlow(anonymous: Boolean, otherSide: FlowSession) : AbstractConfidentialAwareCashResponderFlow<Unit>(anonymous, otherSide) {
    @Suspendable
    override fun respond() {
        // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}

/**
 * Initiates a flow that sends cash to a recipient.
 *
 * @param amount the amount of a currency to pay to the recipient.
 * @param recipient the party to pay the currency to.
 * @param issuerConstraint if specified, the payment will be made using only cash issued by the given parties.
 * @param anonymous whether to anonymous the recipient party. Should be true for normal usage, but may be false
 * for testing purposes.
 */
@StartableByRPC
@InitiatingFlow
open class CashPaymentFlow(
        amount: Amount<Currency>,
        recipient: Party,
        anonymous: Boolean,
        progressTracker: ProgressTracker,
        issuerConstraint: Set<Party> = emptySet()) : AbstractCashPaymentFlow(amount, issuerConstraint, anonymous, recipient, progressTracker) {
    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: Amount<Currency>, recipient: Party) : this(amount, recipient, true, tracker())
    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: Amount<Currency>, recipient: Party, anonymous: Boolean) : this(amount, recipient, anonymous, tracker())
    constructor(request: PaymentRequest) : this(request.amount, request.recipient, request.anonymous, tracker(), request.issuerConstraint)

    @CordaSerializable
    class PaymentRequest(amount: Amount<Currency>,
                         val recipient: Party,
                         val anonymous: Boolean,
                         val issuerConstraint: Set<Party> = emptySet()) : AbstractRequest(amount)
}

@InitiatedBy(CashPaymentFlow::class)
class CashPaymentResponderFlow(otherSide: FlowSession) : AbstractCashPaymentResponderFlow(false, otherSide)

@InitiatingFlow
class CashPaymentAnonymousFlow(creator: AbstractCashPaymentFlow) : AbstractCashPaymentFlow(creator)

@InitiatedBy(CashPaymentAnonymousFlow::class)
class CashPaymentResponderAnonymousFlow(otherSide: FlowSession) : AbstractCashPaymentResponderFlow(true, otherSide)
