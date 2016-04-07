/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.testing

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import contracts.InterestRateSwap
import core.*
import core.crypto.SecureHash
import core.node.services.FixedIdentityService
import core.node.services.linearHeadsOfType
import core.utilities.JsonSupport
import protocols.TwoPartyDealProtocol
import java.time.LocalDate
import java.util.*


/**
 * A simulation in which banks execute interest rate swaps with each other, including the fixing events.
 */
class IRSSimulation(runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(runAsync, latencyInjector) {
    val om = JsonSupport.createDefaultMapper(FixedIdentityService(network.identities))

    init {
        currentDay = LocalDate.of(2016, 3, 10)   // Should be 12th but the actual first fixing date gets rolled backwards.
    }

    private val executeOnNextIteration = Collections.synchronizedList(LinkedList<() -> Unit>())

    override fun start() {
        startIRSDealBetween(0, 1).success {
            // Next iteration is a pause.
            executeOnNextIteration.add {}
            executeOnNextIteration.add {
                // Keep fixing until there's no more left to do.
                doNextFixing(0, 1)?.addListener(object : Runnable {
                    override fun run() {
                        // Pause for an iteration.
                        executeOnNextIteration.add {}
                        executeOnNextIteration.add {
                            doNextFixing(0, 1)?.addListener(this, RunOnCallerThread)
                        }
                    }
                }, RunOnCallerThread)
            }
        }
    }

    private fun doNextFixing(i: Int, j: Int): ListenableFuture<*>? {
        println("Doing a fixing between $i and $j")
        val node1: SimulatedNode = banks[i]
        val node2: SimulatedNode = banks[j]

        val sessionID = random63BitValue()
        val swaps: Map<SecureHash, StateAndRef<InterestRateSwap.State>> = node1.services.walletService.linearHeadsOfType<InterestRateSwap.State>()
        val theDealRef: StateAndRef<InterestRateSwap.State> = swaps.values.single()

        // Do we have any more days left in this deal's lifetime? If not, return.
        val nextFixingDate = theDealRef.state.calculation.nextFixingDate() ?: return null
        extraNodeLabels[node1] = "Fixing event on $nextFixingDate"
        extraNodeLabels[node2] = "Fixing event on $nextFixingDate"

        // For some reason the first fix is always before the effective date.
        if (nextFixingDate > currentDay)
            currentDay = nextFixingDate

        val sideA = TwoPartyDealProtocol.Floater(node2.net.myAddress, sessionID, timestamper.info,
                theDealRef, node1.services.keyManagementService.freshKey(), sessionID)
        val sideB = TwoPartyDealProtocol.Fixer(node1.net.myAddress, timestamper.info.identity,
                theDealRef, sessionID)

        linkConsensus(listOf(node1, node2, regulators[0]), sideB)
        linkProtocolProgress(node1, sideA)
        linkProtocolProgress(node2, sideB)

        // We have to start the protocols in separate iterations, as adding to the SMM effectively 'iterates' that node
        // in the simulation, so if we don't do this then the two sides seem to act simultaneously.

        val retFuture = SettableFuture.create<Any>()
        val futA = node1.smm.add("floater", sideA)
        executeOnNextIteration += {
            val futB = node2.smm.add("fixer", sideB)
            Futures.allAsList(futA, futB).then {
                retFuture.set(null)
            }
        }
        return retFuture
    }

    private fun startIRSDealBetween(i: Int, j: Int): ListenableFuture<SignedTransaction> {
        val node1: SimulatedNode = banks[i]
        val node2: SimulatedNode = banks[j]

        extraNodeLabels[node1] = "Setting up deal"
        extraNodeLabels[node2] = "Setting up deal"

        // We load the IRS afresh each time because the leg parts of the structure aren't data classes so they don't
        // have the convenient copy() method that'd let us make small adjustments. Instead they're partly mutable.
        // TODO: We should revisit this in post-Excalibur cleanup and fix, e.g. by introducing an interface.
        val irs = om.readValue<InterestRateSwap.State>(javaClass.getResource("trade.json"))
        irs.fixedLeg.fixedRatePayer = node1.info.identity
        irs.floatingLeg.floatingRatePayer = node2.info.identity

        if (irs.fixedLeg.effectiveDate < irs.floatingLeg.effectiveDate)
            currentDay = irs.fixedLeg.effectiveDate
        else
            currentDay = irs.floatingLeg.effectiveDate

        val sessionID = random63BitValue()

        val instigator = TwoPartyDealProtocol.Instigator(node2.net.myAddress, timestamper.info,
                irs, node1.services.keyManagementService.freshKey(), sessionID)
        val acceptor = TwoPartyDealProtocol.Acceptor(node1.net.myAddress, timestamper.info.identity,
                irs, sessionID)

        // TODO: Eliminate the need for linkProtocolProgress
        linkConsensus(listOf(node1, node2, regulators[0]), acceptor)
        linkProtocolProgress(node1, instigator)
        linkProtocolProgress(node2, acceptor)

        val instigatorFuture: ListenableFuture<SignedTransaction> = node1.smm.add("instigator", instigator)

        return Futures.transformAsync(Futures.allAsList(instigatorFuture, node2.smm.add("acceptor", acceptor))) {
            instigatorFuture
        }
    }

    override fun iterate() {
        if (executeOnNextIteration.isNotEmpty())
            executeOnNextIteration.removeAt(0)()
        super.iterate()
    }
}