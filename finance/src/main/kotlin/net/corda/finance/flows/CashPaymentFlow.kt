package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import java.util.*

object CashPaymentFlow {
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
    open class Initiate(
            val amount: Amount<Currency>,
            val recipient: Party,
            val anonymous: Boolean,
            progressTracker: ProgressTracker,
            val issuerConstraint: Set<Party> = emptySet()) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {
        /** A straightforward constructor that constructs spends using cash states of any issuer. */
        constructor(amount: Amount<Currency>, recipient: Party) : this(amount, recipient, true, tracker())
        /** A straightforward constructor that constructs spends using cash states of any issuer. */
        constructor(amount: Amount<Currency>, recipient: Party, anonymous: Boolean) : this(amount, recipient, anonymous, tracker())
        constructor(request: PaymentRequest) : this(request.amount, request.recipient, request.anonymous, tracker(), request.issuerConstraint)

        @Suspendable
        override fun call(): AbstractCashFlow.Result {
            val accepted = sendAndReceive<Boolean>(recipient, PaymentProposal(amount, anonymous)).unwrap { it }
            if (!accepted) {
                throw CashException("Proposed payment rejected by counterparty $recipient")
            }
            progressTracker.currentStep = GENERATING_ID
            val txIdentities = if (anonymous) {
                subFlow(TransactionKeyFlow(recipient))
            } else {
                emptyMap<Party, AnonymousParty>()
            }
            val anonymousRecipient = txIdentities[recipient] ?: recipient
            progressTracker.currentStep = GENERATING_TX
            val builder = TransactionBuilder(null as Party?)
            // TODO: Have some way of restricting this to states the caller controls
            val (spendTX, keysForSigning) = try {
                Cash.generateSpend(serviceHub,
                        builder,
                        amount,
                        anonymousRecipient,
                        issuerConstraint)
            } catch (e: InsufficientBalanceException) {
                throw CashException("Insufficient cash for spend: ${e.message}", e)
            }

            progressTracker.currentStep = SIGNING_TX
            val tx = serviceHub.signInitialTransaction(spendTX, keysForSigning)

            progressTracker.currentStep = FINALISING_TX
            finaliseTx(setOf(recipient), tx, "Unable to notarise spend")
            return Result(tx, anonymousRecipient)
        }
    }

    @InitiatedBy(CashPaymentFlow.Initiate::class)
    class Receive(val otherSide: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val proposal = receive<PaymentProposal>(otherSide)
            // TODO: Provide hooks for KYC/AML verification logic, don't just accept everything
            send(otherSide, true)
        }
    }

    /**
     * Details of a proposed payment for the otherSide to accept/reject.
     */
    @CordaSerializable
    data class PaymentProposal(val amount: Amount<Currency>, val anonymous: Boolean)

    @CordaSerializable
    class PaymentRequest(amount: Amount<Currency>, val recipient: Party, val anonymous: Boolean, val issuerConstraint: Set<Party> = emptySet()) : AbstractCashFlow.AbstractRequest(amount)
}