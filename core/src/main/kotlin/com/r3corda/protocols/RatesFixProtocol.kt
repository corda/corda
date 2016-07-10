package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.Fix
import com.r3corda.core.contracts.FixOf
import com.r3corda.core.contracts.TransactionBuilder
import com.r3corda.core.contracts.WireTransaction
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.suggestInterestRateAnnouncementTimeWindow
import java.math.BigDecimal
import java.time.Duration
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
                            private val timeOut: Duration,
                            override val progressTracker: ProgressTracker = RatesFixProtocol.tracker(fixOf.name)) : ProtocolLogic<Unit>() {

    companion object {
        val TOPIC = "platform.rates.interest.fix"

        class QUERYING(val name: String) : ProgressTracker.Step("Querying oracle for $name interest rate")
        object WORKING : ProgressTracker.Step("Working with data returned by oracle")
        object SIGNING : ProgressTracker.Step("Requesting confirmation signature from interest rate oracle")

        fun tracker(fixName: String) = ProgressTracker(QUERYING(fixName), WORKING, SIGNING)
    }

    override val topic: String get() = TOPIC

    class FixOutOfRange(val byAmount: BigDecimal) : Exception()

    data class QueryRequest(val queries: List<FixOf>, override val replyToParty: Party, override val sessionID: Long, val deadline: Instant) : PartyRequestMessage
    data class SignRequest(val tx: WireTransaction, override val replyToParty: Party, override val sessionID: Long) : PartyRequestMessage

    @Suspendable
    override fun call() {
        progressTracker.currentStep = progressTracker.steps[1]
        val fix = query()
        progressTracker.currentStep = WORKING
        checkFixIsNearExpected(fix)
        tx.addCommand(fix, oracle.owningKey)
        beforeSigning(fix)
        progressTracker.currentStep = SIGNING
        tx.addSignatureUnchecked(sign())
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

    @Suspendable
    private fun sign(): DigitalSignature.LegallyIdentifiable {
        val sessionID = random63BitValue()
        val wtx = tx.toWireTransaction()
        val req = SignRequest(wtx, serviceHub.storageService.myLegalIdentity, sessionID)
        val resp = sendAndReceive<DigitalSignature.LegallyIdentifiable>(oracle, 0, sessionID, req)

        return resp.validate { sig ->
            check(sig.signer == oracle)
            tx.checkSignature(sig)
            sig
        }
    }

    @Suspendable
    private fun query(): Fix {
        val sessionID = random63BitValue()
        val deadline = suggestInterestRateAnnouncementTimeWindow(fixOf.name, oracle.name, fixOf.forDay).end
        val req = QueryRequest(listOf(fixOf), serviceHub.storageService.myLegalIdentity, sessionID, deadline)
        // TODO: add deadline to receive
        val resp = sendAndReceive<ArrayList<Fix>>(oracle, 0, sessionID, req)

        return resp.validate {
            val fix = it.first()
            // Check the returned fix is for what we asked for.
            check(fix.of == fixOf)
            fix
        }
    }
}
