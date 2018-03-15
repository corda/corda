/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.netmap.simulation

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.queryBy
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.flows.TwoPartyDealFlow.Acceptor
import net.corda.finance.flows.TwoPartyDealFlow.AutoOffer
import net.corda.finance.flows.TwoPartyDealFlow.Instigator
import net.corda.finance.plugin.registerFinanceJSONMappers
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.flows.FixingFlow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.startFlow
import net.corda.testing.node.makeTestIdentityService
import rx.Observable
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.allOf

/**
 * A simulation in which banks execute interest rate swaps with each other, including the fixing events.
 */
class IRSSimulation(networkSendManuallyPumped: Boolean, runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(networkSendManuallyPumped, runAsync, latencyInjector) {
    lateinit var om: ObjectMapper

    init {
        currentDateAndTime = LocalDate.of(2016, 3, 8).atStartOfDay()
    }

    private val executeOnNextIteration = Collections.synchronizedList(LinkedList<() -> Unit>())

    override fun startMainSimulation(): CompletableFuture<Unit> {
        om = JacksonSupport.createInMemoryMapper(makeTestIdentityService(*(banks + regulators + ratesOracle).flatMap { it.started!!.info.legalIdentitiesAndCerts }.toTypedArray()))
        registerFinanceJSONMappers(om)

        return startIRSDealBetween(0, 1).thenCompose {
            val future = CompletableFuture<Unit>()
            // Next iteration is a pause.
            executeOnNextIteration.add {}
            executeOnNextIteration.add {
                // Keep fixing until there's no more left to do.
                val initialFixFuture = doNextFixing(0, 1)
                fun onFailure(t: Throwable) {
                    future.completeExceptionally(t)
                }

                fun onSuccess() {
                    // Pause for an iteration.
                    executeOnNextIteration.add {}
                    executeOnNextIteration.add {
                        val f = doNextFixing(0, 1)
                        if (f != null) {
                            f.handle { _, throwable ->
                                if (throwable == null) {
                                    onSuccess()
                                } else {
                                    onFailure(throwable)
                                }
                            }
                        } else {
                            // All done!
                            future.complete(Unit)
                        }
                    }
                }
                initialFixFuture!!.handle { _, throwable ->
                    if (throwable == null) {
                        onSuccess()
                    } else {
                        onFailure(throwable)
                    }
                }
            }
            future
        }
    }

    private fun doNextFixing(i: Int, j: Int): CompletableFuture<Void>? {
        println("Doing a fixing between $i and $j")
        val node1 = banks[i].started!!
        val node2 = banks[j].started!!

        val swaps =
                node1.database.transaction {
                    node1.services.vaultService.queryBy<InterestRateSwap.State>().states
                }
        val theDealRef: StateAndRef<InterestRateSwap.State> = swaps.single()

        // Do we have any more days left in this deal's lifetime? If not, return.
        val nextFixingDate = theDealRef.state.data.calculation.nextFixingDate() ?: return null
        extraNodeLabels[node1.internals] = "Fixing event on $nextFixingDate"
        extraNodeLabels[node2.internals] = "Fixing event on $nextFixingDate"

        // Complete the future when the state has been consumed on both nodes
        val futA = node1.services.vaultService.whenConsumed(theDealRef.ref)
        val futB = node2.services.vaultService.whenConsumed(theDealRef.ref)

        showConsensusFor(listOf(node1.internals, node2.internals, regulators[0]))

        // For some reason the first fix is always before the effective date.
        if (nextFixingDate > currentDateAndTime.toLocalDate())
            currentDateAndTime = nextFixingDate.atTime(15, 0)

        return allOf(futA.toCompletableFuture(), futB.toCompletableFuture())
    }

    private fun startIRSDealBetween(i: Int, j: Int): CompletableFuture<SignedTransaction> {
        val node1 = banks[i].started!!
        val node2 = banks[j].started!!

        extraNodeLabels[node1.internals] = "Setting up deal"
        extraNodeLabels[node2.internals] = "Setting up deal"

        // We load the IRS afresh each time because the leg parts of the structure aren't data classes so they don't
        // have the convenient copy() method that'd let us make small adjustments. Instead they're partly mutable.
        // TODO: We should revisit this in post-Excalibur cleanup and fix, e.g. by introducing an interface.

        val resourceAsStream = javaClass.classLoader.getResourceAsStream("net/corda/irs/web/simulation/trade.json")
                ?: error("Trade representation cannot be loaded.")
        val irs = om.readValue<InterestRateSwap.State>(resourceAsStream
                .reader()
                .readText()
                .replace("oracleXXX", RatesOracleNode.RATES_SERVICE_NAME.toString()))
        irs.fixedLeg.fixedRatePayer = node1.info.singleIdentity()
        irs.floatingLeg.floatingRatePayer = node2.info.singleIdentity()
        node1.registerInitiatedFlow(FixingFlow.Fixer::class.java)
        node2.registerInitiatedFlow(FixingFlow.Fixer::class.java)
        @InitiatingFlow
        class StartDealFlow(val otherParty: Party,
                            val payload: AutoOffer) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val session = initiateFlow(otherParty)
                return subFlow(Instigator(session, payload))
            }
        }

        @InitiatedBy(StartDealFlow::class)
        class AcceptDealFlow(otherSession: FlowSession) : Acceptor(otherSession)
        val acceptDealFlows: Observable<AcceptDealFlow> = node2.registerInitiatedFlow(AcceptDealFlow::class.java)
        val acceptorTxFuture = acceptDealFlows.toFuture().toCompletableFuture().thenCompose {
            uncheckedCast<FlowStateMachine<*>, FlowStateMachine<SignedTransaction>>(it.stateMachine).resultFuture.toCompletableFuture()
        }

        showProgressFor(listOf(node1, node2))
        showConsensusFor(listOf(node1.internals, node2.internals, regulators[0]))

        val instigator = StartDealFlow(
                node2.info.singleIdentity(),
                AutoOffer(mockNet.defaultNotaryIdentity, irs)) // TODO Pass notary as parameter to Simulation.
        val instigatorTxFuture = node1.services.startFlow(instigator).resultFuture

        return allOf(instigatorTxFuture.toCompletableFuture(), acceptorTxFuture).thenCompose { instigatorTxFuture.toCompletableFuture() }
    }

    override fun iterate(): InMemoryMessagingNetwork.MessageTransfer? {
        if (executeOnNextIteration.isNotEmpty())
            executeOnNextIteration.removeAt(0)()
        return super.iterate()
    }
}
