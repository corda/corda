package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.Fix
import com.r3corda.core.contracts.FixOf
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.suggestInterestRateAnnouncementTimeWindow
import com.r3corda.protocols.RatesFixProtocol.FixOutOfRange
import java.math.BigDecimal
import java.time.Instant
import java.util.*

// This code is unit tested in NodeInterestRates.kt

/**
 * This protocol queries the given oracle for an interest rate fix, and if it is within the given tolerance embeds the
 * fix in the transaction and then proceeds to get the oracle to sign it. Although the [call] method combines the query
 * and signing step, you can run the steps individually by constructing this object and then using the public methods
 * for each step.
 *
 * @throws FixOutOfRange if the returned fix was further away from the expected rate by the given amount.
 */
open class RatesFixProtocol(protected val tx: TransactionBuilder,
                            private val oracle: Party,
                            private val fixOf: FixOf,
                            private val expectedRate: BigDecimal,
                            private val rateTolerance: BigDecimal,
                            override val progressTracker: ProgressTracker = RatesFixProtocol.tracker(fixOf.name)) : ProtocolLogic<Unit>() {

    companion object {
        class QUERYING(val name: String) : ProgressTracker.Step("Querying oracle for $name interest rate")
        object WORKING : ProgressTracker.Step("Working with data returned by oracle")
        object SIGNING : ProgressTracker.Step("Requesting confirmation signature from interest rate oracle")

        fun tracker(fixName: String) = ProgressTracker(QUERYING(fixName), WORKING, SIGNING)
    }

    class FixOutOfRange(@Suppress("unused") val byAmount: BigDecimal) : Exception("Fix out of range by $byAmount")

    data class QueryRequest(val queries: List<FixOf>, val deadline: Instant)
    data class SignRequest(val tx: WireTransaction)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = progressTracker.steps[1]
        val fix = subProtocol(FixQueryProtocol(fixOf, oracle))
        progressTracker.currentStep = WORKING
        checkFixIsNearExpected(fix)
        tx.addCommand(fix, oracle.owningKey)
        beforeSigning(fix)
        progressTracker.currentStep = SIGNING
        val signature = subProtocol(FixSignProtocol(tx, oracle))
        tx.addSignatureUnchecked(signature)
    }

    /**
     * You can override this to perform any additional work needed after the fix is added to the transaction but
     * before it's sent back to the oracle for signing (for example, adding output states that depend on the fix).
     */
    @Suspendable
    protected open fun beforeSigning(fix: Fix) {
    }

    private fun checkFixIsNearExpected(fix: Fix) {
        val delta = (fix.value - expectedRate).abs()
        if (delta > rateTolerance) {
            // TODO: Kick to a user confirmation / ui flow if it's out of bounds instead of raising an exception.
            throw FixOutOfRange(delta)
        }
    }


    class FixQueryProtocol(val fixOf: FixOf, val oracle: Party) : ProtocolLogic<Fix>() {
        @Suspendable
        override fun call(): Fix {
            val deadline = suggestInterestRateAnnouncementTimeWindow(fixOf.name, oracle.name, fixOf.forDay).end
            // TODO: add deadline to receive
            val resp = sendAndReceive<ArrayList<Fix>>(oracle, QueryRequest(listOf(fixOf), deadline))

            return resp.unwrap {
                val fix = it.first()
                // Check the returned fix is for what we asked for.
                check(fix.of == fixOf)
                fix
            }
        }
    }


    class FixSignProtocol(val tx: TransactionBuilder, val oracle: Party) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {
        @Suspendable
        override fun call(): DigitalSignature.LegallyIdentifiable {
            val wtx = tx.toWireTransaction()
            val resp = sendAndReceive<DigitalSignature.LegallyIdentifiable>(oracle, SignRequest(wtx))

            return resp.unwrap { sig ->
                check(sig.signer == oracle)
                tx.checkSignature(sig)
                sig
            }
        }
    }

}
