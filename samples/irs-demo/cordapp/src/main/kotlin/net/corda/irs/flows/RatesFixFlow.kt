package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.Fix
import net.corda.finance.contracts.FixOf
import net.corda.irs.flows.RatesFixFlow.FixOutOfRange
import java.math.BigDecimal
import java.util.function.Predicate

// This code is unit tested in NodeInterestRates.kt

/**
 * This flow queries the given oracle for an interest rate fix, and if it is within the given tolerance embeds the
 * fix in the transaction and then proceeds to get the oracle to sign it. Although the [call] method combines the query
 * and signing step, you can run the steps individually by constructing this object and then using the public methods
 * for each step.
 *
 * @throws FixOutOfRange if the returned fix was further away from the expected rate by the given amount.
 */
open class RatesFixFlow(protected val tx: TransactionBuilder,
                        protected val oracle: Party,
                        protected val fixOf: FixOf,
                        protected val expectedRate: BigDecimal,
                        protected val rateTolerance: BigDecimal,
                        override val progressTracker: ProgressTracker = RatesFixFlow.tracker(fixOf.name)) : FlowLogic<TransactionSignature>() {

    companion object {
        class QUERYING(val name: String) : ProgressTracker.Step("Querying oracle for $name interest rate")
        object WORKING : ProgressTracker.Step("Working with data returned by oracle")
        object SIGNING : ProgressTracker.Step("Requesting confirmation signature from interest rate oracle")

        fun tracker(fixName: String) = ProgressTracker(QUERYING(fixName), WORKING, SIGNING)
    }

    class FixOutOfRange(@Suppress("unused") val byAmount: BigDecimal) : FlowException("Fix out of range by $byAmount")

    @CordaSerializable
    data class QueryRequest(val queries: List<FixOf>)

    @CordaSerializable
    data class SignRequest(val ftx: FilteredTransaction)

    // DOCSTART 2
    @Suspendable
    override fun call(): TransactionSignature {
        progressTracker.currentStep = progressTracker.steps[1]
        val fix = subFlow(FixQueryFlow(fixOf, oracle))
        progressTracker.currentStep = WORKING
        checkFixIsNearExpected(fix)
        tx.addCommand(fix, oracle.owningKey)
        beforeSigning(fix)
        progressTracker.currentStep = SIGNING
        val mtx = tx.toWireTransaction(serviceHub).buildFilteredTransaction(Predicate { filtering(it) })
        return subFlow(FixSignFlow(tx, oracle, mtx))
    }
    // DOCEND 2

    /**
     * You can override this to perform any additional work needed after the fix is added to the transaction but
     * before it's sent back to the oracle for signing (for example, adding output states that depend on the fix).
     */
    @Suspendable
    protected open fun beforeSigning(fix: Fix) {
    }

    /**
     * Filtering functions over transaction, used to build partial transaction with partial Merkle tree presented to oracle.
     * When overriding be careful when making the sub-class an anonymous or inner class (object declarations in Kotlin),
     * because that kind of classes can access variables from the enclosing scope and cause serialization problems when
     * checkpointed.
     */
    @Suspendable
    protected open fun filtering(elem: Any): Boolean = false

    private fun checkFixIsNearExpected(fix: Fix) {
        val delta = (fix.value - expectedRate).abs()
        if (delta > rateTolerance) {
            // TODO: Kick to a user confirmation / ui flow if it's out of bounds instead of raising an exception.
            throw FixOutOfRange(delta)
        }
    }

    // DOCSTART 1
    @InitiatingFlow
    class FixQueryFlow(val fixOf: FixOf, val oracle: Party) : FlowLogic<Fix>() {
        @Suspendable
        override fun call(): Fix {
            val oracleSession = initiateFlow(oracle)
            // TODO: add deadline to receive
            val resp = oracleSession.sendAndReceive<List<Fix>>(QueryRequest(listOf(fixOf)))

            return resp.unwrap {
                val fix = it.first()
                // Check the returned fix is for what we asked for.
                check(fix.of == fixOf)
                fix
            }
        }
    }

    @InitiatingFlow
    class FixSignFlow(val tx: TransactionBuilder, val oracle: Party,
                      val partialMerkleTx: FilteredTransaction) : FlowLogic<TransactionSignature>() {
        @Suspendable
        override fun call(): TransactionSignature {
            val oracleSession = initiateFlow(oracle)
            val resp = oracleSession.sendAndReceive<TransactionSignature>(SignRequest(partialMerkleTx))
            return resp.unwrap { sig ->
                check(oracleSession.counterparty.owningKey.isFulfilledBy(listOf(sig.by)))
                tx.toWireTransaction(serviceHub).checkSignature(sig)
                sig
            }
        }
    }
    // DOCEND 1
}
