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
import com.r3.corda.enterprise.perftestcordapp.contracts.LinearStateBatchNotariseContract
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.times
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * A flow that generates N linear states, and then evolves them X times, as close to the specified rate as possible.
 *
 * @property notary The notary to use for notarising the evolution transactions (not the initial, which is unnotarised).
 * @property n The number of states per transaction.
 * @property x The number of iterations to do (so overall, we generate x+1 transactions).
 * @property logIterations If true, will log at info level the iteration the flow is on (helpful if trying to see progress in node logs).
 * @property transactionsPerSecond A target number of transactions to generate per second.  The target may not be achieved.
 */
@StartableByRPC
class LinearStateBatchNotariseFlow(private val notary: Party,
                                   private val n: Int,
                                   private val x: Int,
                                   private val logIterations: Boolean,
                                   private val transactionsPerSecond: Double
) : FlowLogic<LinearStateBatchNotariseFlow.Result>() {
    companion object {
        object GENERATING_INITIAL_TX : ProgressTracker.Step("Generating initial transaction")
        object EVOLVING_STATES_TX : ProgressTracker.Step("Generating transaction to evolve states")
        object SENDING_RESULTS : ProgressTracker.Step("Sending results")

        fun tracker() = ProgressTracker(GENERATING_INITIAL_TX, EVOLVING_STATES_TX, SENDING_RESULTS)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): Result {
        progressTracker.currentStep = GENERATING_INITIAL_TX
        val us = serviceHub.myInfo.legalIdentities.first()
        var inputTx = buildInitialTx(us)
        progressTracker.currentStep = EVOLVING_STATES_TX
        val durationOfEachIteration = Duration.ofHours(1).dividedBy((transactionsPerSecond * TimeUnit.SECONDS.convert(1, TimeUnit.HOURS)).toLong())
        val measurements = LinkedList<Measurement>()
        val iterationStartTime = serviceHub.clock.instant()
        (0 until x).forEach { iterationNumber ->
            val expectedTimeOfNextIteration = iterationStartTime.plus(durationOfEachIteration.times(iterationNumber.toLong()))
            val sleepDuration = Duration.between(serviceHub.clock.instant(), expectedTimeOfNextIteration)
            if (!sleepDuration.isNegative && !sleepDuration.isZero) {
                sleep(sleepDuration)
            }
            if (logIterations) {
                logger.info("ITERATION ${iterationNumber + 1} of $x, with $n states. Slept for $sleepDuration")
            }
            buildEvolveTx(inputTx, us, iterationNumber, sleepDuration).apply {
                inputTx = first
                measurements += second
            }
        }
        val totalTime = Duration.between(iterationStartTime, serviceHub.clock.instant()).toMillis()
        val txDurations = measurements.map { Duration.between(it.start, it.end).toMillis() }.sortedDescending()
        val p95Index = Math.floor(x * 0.05).toInt()
        logger.info("Notarised $x transactions ($n states/tx) in ${totalTime}ms (avg ${totalTime / x.toDouble()}ms/tx, slowest ${txDurations[0]}ms; 95% < ${txDurations[p95Index]}ms)")
        progressTracker.currentStep = SENDING_RESULTS
        return Result(measurements)
    }

    @Suspendable
    private fun buildEvolveTx(inputTx: SignedTransaction, us: Party, iterationNumber: Int, sleepDuration: Duration): Pair<SignedTransaction, Measurement> {
        val tx = assembleEvolveTx(inputTx, us)
        val startTime = serviceHub.clock.instant()
        val stx = finaliseTx(tx, "Unable to notarise initial evolution transaction, iteration $iterationNumber.")
        val endTime = serviceHub.clock.instant()
        return stx to Measurement(startTime, endTime, sleepDuration)
    }

    @Suspendable
    private fun assembleEvolveTx(inputTx: SignedTransaction, us: Party): SignedTransaction {
        val wtx = inputTx.tx
        val builder = TransactionBuilder(notary)
        (0 until n).forEach { outputIndex ->
            val input = wtx.outRef<LinearStateBatchNotariseContract.State>(outputIndex)
            builder.addInputState(input)
            builder.addOutputState(TransactionState(LinearStateBatchNotariseContract.State(input.state.data.linearId, us, serviceHub.clock.instant()), LinearStateBatchNotariseContract.CP_PROGRAM_ID, notary))
        }
        builder.addCommand(LinearStateBatchNotariseContract.Commands.Evolve(), us.owningKey)
        builder.setTimeWindow(TimeWindow.fromOnly(serviceHub.clock.instant()))
        val tx = serviceHub.signInitialTransaction(builder, us.owningKey)
        return tx
    }

    @Suspendable
    private fun buildInitialTx(us: Party): SignedTransaction {
        val tx = assembleInitialTx(us)
        return finaliseTx(tx, "Unable to notarise initial generation transaction.")
    }

    @Suspendable
    private fun assembleInitialTx(us: Party): SignedTransaction {
        val builder = TransactionBuilder(notary)
        (0 until n).forEach { outputIndex ->
            builder.addOutputState(TransactionState(LinearStateBatchNotariseContract.State(UniqueIdentifier(), us, serviceHub.clock.instant()), LinearStateBatchNotariseContract.CP_PROGRAM_ID, notary))
        }
        builder.addCommand(LinearStateBatchNotariseContract.Commands.Create(), us.owningKey)
        builder.setTimeWindow(TimeWindow.fromOnly(serviceHub.clock.instant()))
        val tx = serviceHub.signInitialTransaction(builder, us.owningKey)
        return tx
    }

    @Suspendable
    protected fun finaliseTx(tx: SignedTransaction, message: String): SignedTransaction {
        try {
            return subFlow(FinalityFlow(tx))
        } catch (e: NotaryException) {
            throw FlowException(message, e)
        }
    }

    @CordaSerializable
    data class Result(val measurements: List<Measurement>)

    @CordaSerializable
    data class Measurement(val start: Instant, val end: Instant, val delay: Duration)
}


