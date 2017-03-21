package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.crypto.keys
import net.corda.core.crypto.toStringShort
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.security.KeyPair
import java.util.*

/**
 * Initiates a flow that sends cash to a recipient.
 *
 * @param amount the amount of a currency to pay to the recipient.
 * @param recipient the party to pay the currency to.
 * @param issuerConstraint if specified, the payment will be made using only cash issued by the given parties.
 */
open class CashPaymentFlow(
        val amount: Amount<Currency>,
        val recipient: Party,
        progressTracker: ProgressTracker,
        val issuerConstraint: Set<Party>? = null) : AbstractCashFlow(progressTracker) {
    /** A straightforward constructor that constructs spends using cash states of any issuer. */
    constructor(amount: Amount<Currency>, recipient: Party) : this(amount, recipient, tracker())

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATING_TX
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        val (spendTX, keysForSigning) = try {
            serviceHub.vaultService.generateSpend(
                    builder,
                    amount,
                    recipient.owningKey,
                    issuerConstraint)
        } catch (e: InsufficientBalanceException) {
            throw CashException("Insufficient cash for spend: ${e.message}", e)
        }

        progressTracker.currentStep = SIGNING_TX
        keysForSigning.keys.forEach {
            val key = serviceHub.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
            builder.signWith(KeyPair(it, key))
        }

        progressTracker.currentStep = FINALISING_TX
        val tx = spendTX.toSignedTransaction(checkSufficientSignatures = false)
        finaliseTx(setOf(recipient), tx, "Unable to notarise spend")
        return tx
    }
}