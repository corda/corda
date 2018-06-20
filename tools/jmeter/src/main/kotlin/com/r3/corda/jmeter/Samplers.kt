/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.jmeter

import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.POUNDS
import com.r3.corda.enterprise.perftestcordapp.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import org.apache.jmeter.config.Argument
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.SampleResult
import java.util.*

/**
 * A base sampler that looks up identities via RPC ready for starting flows, to be extended and specialised as required.
 */
abstract class AbstractSampler : BaseFlowSampler() {
    lateinit var notaryIdentity: Party

    companion object JMeterProperties {
        val notary = Argument("notaryName", "", "<meta>", "The X500 name of the notary.")
    }

    protected fun getIdentity( rpc: CordaRPCOps, testContext: JavaSamplerContext, arg: Argument):Party{
        if (!testContext.containsParameter(arg.name)) {
            throw IllegalStateException("You must specify the '${arg.name}' property.")
        }
        val argName = CordaX500Name.parse(testContext.getParameter(arg.name))
        return rpc.wellKnownPartyFromX500Name(argName) ?: throw IllegalStateException("Don't know $argName")
    }

    protected fun getNotaryIdentity(rpc: CordaRPCOps, testContext: JavaSamplerContext) {
        notaryIdentity = getIdentity(rpc,testContext, notary)
    }
}

/**
 * A sampler for calling EmptyFlow.
 */
class EmptyFlowSampler : AbstractSampler() {
    override val additionalArgs: Set<Argument> = emptySet()

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<EmptyFlow> {
        return FlowInvoke<EmptyFlow>(EmptyFlow::class.java, emptyArray())
    }
}

/**
 * A sampler for calling CashIssueFlow.
 *
 * TODO: add more configurable parameters (reference, amount etc) if there is a requirement.
 */
class CashIssueSampler : AbstractSampler() {
    override val additionalArgs: Set<Argument> = setOf(notary)

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy, testContext)
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<CashIssueFlow> {
        val amount = 1_100_000_000_000.DOLLARS
        return FlowInvoke<CashIssueFlow>(CashIssueFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), notaryIdentity))
    }
}

/**
 * A sampler that issues cash and pays it to a specified party, thus invoking the notary and the payee
 * via P2P
 */
class CashIssueAndPaySampler : AbstractSampler() {
    companion object JMeterProperties {
        val otherParty = Argument("otherPartyName", "", "<meta>", "The X500 name of the payee.")
        val coinSelection = Argument("useCoinSelection", "true", "<meta>", "True to use coin selection and false (or anything else) to avoid coin selection.")
        val anonymousIdentities = Argument("anonymousIdentities", "true", "<meta>", "True to use anonymous identities and false (or anything else) to use well known identities.")
    }

    lateinit var counterParty: Party
    var useCoinSelection: Boolean = true
    var useAnonymousIdentities: Boolean = true

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy,testContext)
        counterParty = getIdentity(rpcProxy, testContext, otherParty)
        useCoinSelection = testContext.getParameter(coinSelection.name, coinSelection.value).toBoolean()
        useAnonymousIdentities = testContext.getParameter(anonymousIdentities.name, anonymousIdentities.value).toBoolean()
    }


    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<*> {
        val amount = 2_000_000.POUNDS
        if (useCoinSelection) {
            return FlowInvoke<CashIssueAndPaymentFlow>(CashIssueAndPaymentFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), counterParty, useAnonymousIdentities, notaryIdentity))
        } else {
            return FlowInvoke<CashIssueAndPaymentNoSelection>(CashIssueAndPaymentNoSelection::class.java, arrayOf(amount, OpaqueBytes.of(1), counterParty, useAnonymousIdentities, notaryIdentity))
        }
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override val additionalArgs: Set<Argument>
        get() = setOf(notary, otherParty, coinSelection, anonymousIdentities)
}

/**
 * A sampler that attempts to generate load on the Notary.
 *
 * It builds a transaction of multiple `LinearState`s and then for each iteration transitions each state as a batch in
 * a single transaction and sends it to be notarised.  That way the size of the transaction, both in bytes and number of
 * states, can be varied (not independently currently).
 *
 * The requesting flow does this for a specified number of iterations, and returns a result that is then fed back to JMeter.
 * So don't be surprised if it looks like JMeter is getting no results for a while.  It only receives them when the flow
 * finishes.
 * If JMeter asks for more samples/iterations than that, then another flow is kicked off automatically if the repeat property
 * is set to true, otherwise it will return an error/failure for the next sample.
 *
 * The flow will throttle (if necessary) to maintain a specified number of iterations/transactions per second.  This is
 * aggregated over all iterations, so GC pauses etc shouldn't reduce the overall number of transactions in a time period,
 * unless the node / notary is unable to keep up.  If falling behind, the flow will not pause between transactions.
 */
class LinearStateBatchNotariseSampler : AbstractSampler() {
    companion object JMeterProperties {
        val numberOfStates = Argument("numStates", "1", "<meta>", "Number of linear states to include in each transaction.")
        val numberOfIterations = Argument("numIterations", "1", "<meta>", "Number of iterations / evolutions to do. Each iteration generates one transaction.")
        val logIterations = Argument("enableLog", "false", "<meta>", "Print in the logs what iteration the test is on etc.")
        val numberOfTps = Argument("transactionsPerSecond", "1.0", "<meta>", "Transaction per second target.")
        val repeat = Argument("repeatInvoke", "false", "<meta>", "If true, invoke the flow again if JMeter expects more iterations.")
    }

    var n: Int = 0
    var x: Int = 0
    var log: Boolean = false
    var tps: Double = 1.0
    var reRequest: Boolean = false

    var measurements: LinkedList<LinkedList<LinearStateBatchNotariseFlow.Measurement>> = LinkedList()
    var measurementsSize: Int = 0
    var nextIteration: Int = 0
    var sample: SampleResult? = null

    override val additionalArgs: Set<Argument> = setOf(notary, numberOfStates, numberOfIterations, logIterations, numberOfTps, repeat)

    // At test setup, we fire off one big request via RPC.
    override fun setupTest(context: JavaSamplerContext) {
        measurements.clear()
        super.setupTest(context)
        println("Running test $context")
        super.runTest(context)
    }

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy, testContext)
        n = testContext.getParameter(numberOfStates.name, numberOfStates.value).toInt()
        x = testContext.getParameter(numberOfIterations.name, numberOfIterations.value).toInt()
        log = testContext.getParameter(logIterations.name, logIterations.value).toBoolean()
        tps = testContext.getParameter(numberOfTps.name, numberOfTps.value).toDouble()
        reRequest = testContext.getParameter(repeat.name, repeat.value).toBoolean()
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<LinearStateBatchNotariseFlow> {
        return FlowInvoke<LinearStateBatchNotariseFlow>(LinearStateBatchNotariseFlow::class.java, arrayOf(notaryIdentity, n, x, log, tps))
    }

    override fun additionalFlowResponseProcessing(context: JavaSamplerContext, sample: SampleResult, response: Any?) {
        if (response is LinearStateBatchNotariseFlow.Result && response.measurements.isNotEmpty()) {
            measurements.add(LinkedList(response.measurements))
            measurementsSize += response.measurements.size
        }
        this.sample = sample
    }

    private fun nextMeasurement(context: JavaSamplerContext): LinearStateBatchNotariseFlow.Measurement {
        val firstList = measurements.first()
        val measurement = firstList.remove()
        measurementsSize--
        if (firstList.isEmpty()) {
            measurements.remove()
            nextIteration = 0
            // if a flag is set, run the flow again
            if (reRequest) {
                println("Re-running test $context")
                super.runTest(context)
            }
        }
        return measurement
    }

    // Each iteration of the test returns the next measurement from the large batch request.
    override fun runTest(context: JavaSamplerContext): SampleResult {
        val topLevelSample = sample ?: SampleResult().apply { isSuccessful = false }
        val currentIteration = nextIteration++
        // Build samples based on the response.
        val result = if (topLevelSample.isSuccessful && measurementsSize > 0) {
            val measurement = nextMeasurement(context)
            val result = SampleResult(measurement.end.toEpochMilli(), measurement.end.toEpochMilli() - measurement.start.toEpochMilli())
            val delay = measurement.delay.toMillis()
            if (delay < 0) {
                result.latency = -delay
            }
            result.isSuccessful = true
            result.sampleLabel = "${topLevelSample.sampleLabel}-$currentIteration"
            result
        } else {
            val result = SampleResult(topLevelSample.timeStamp, 0)
            result.isSuccessful = false
            result.sampleLabel = if (!topLevelSample.isSuccessful) "${topLevelSample.sampleLabel}-$currentIteration" else "${topLevelSample.sampleLabel}-END"
            result
        }
        return result
    }
}
