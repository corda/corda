package com.r3corda.node.internal.testing

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.contracts.InterestRateSwap
import com.r3corda.core.RunOnCallerThread
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.failure
import com.r3corda.core.node.services.linearHeadsOfType
import com.r3corda.core.node.services.testing.MockIdentityService
import com.r3corda.core.random63BitValue
import com.r3corda.core.success
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.node.utilities.JsonSupport
import com.r3corda.protocols.TwoPartyDealProtocol
import java.security.KeyPair
import java.time.LocalDate
import java.util.*


/**
 * A simulation in which banks execute interest rate swaps with each other, including the fixing events.
 */
class IRSSimulation(networkSendManuallyPumped: Boolean, runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(networkSendManuallyPumped, runAsync, latencyInjector) {
    val om = JsonSupport.createDefaultMapper(MockIdentityService(network.identities))

    init {
        currentDateAndTime = LocalDate.of(2016, 3, 8).atStartOfDay()
    }

    private var nodeAKey: KeyPair? = null
    private var nodeBKey: KeyPair? = null

    private val executeOnNextIteration = Collections.synchronizedList(LinkedList<() -> Unit>())

    override fun startMainSimulation(): ListenableFuture<Unit> {
        val future = SettableFuture.create<Unit>()

        nodeAKey = banks[0].keyManagement.freshKey()
        nodeBKey = banks[1].keyManagement.freshKey()

        startIRSDealBetween(0, 1).success {
            // Next iteration is a pause.
            executeOnNextIteration.add {}
            executeOnNextIteration.add {
                // Keep fixing until there's no more left to do.
                val initialFixFuture = doNextFixing(0, 1)

                Futures.addCallback(initialFixFuture, object : FutureCallback<Unit> {
                    override fun onFailure(t: Throwable) {
                        future.setException(t)   // Propagate the error.
                    }

                    override fun onSuccess(result: Unit?) {
                        // Pause for an iteration.
                        executeOnNextIteration.add {}
                        executeOnNextIteration.add {
                            val f = doNextFixing(0, 1)
                            if (f != null) {
                                Futures.addCallback(f, this, RunOnCallerThread)
                            } else {
                                // All done!
                                future.set(Unit)
                            }
                        }
                    }
                }, RunOnCallerThread)
            }
        }
        return future
    }

    private fun doNextFixing(i: Int, j: Int): ListenableFuture<Unit>? {
        println("Doing a fixing between $i and $j")
        val node1: SimulatedNode = banks[i]
        val node2: SimulatedNode = banks[j]

        val swaps: Map<SecureHash, StateAndRef<InterestRateSwap.State>> = node1.services.walletService.linearHeadsOfType<InterestRateSwap.State>()
        val theDealRef: StateAndRef<InterestRateSwap.State> = swaps.values.single()

        // Do we have any more days left in this deal's lifetime? If not, return.
        val nextFixingDate = theDealRef.state.data.calculation.nextFixingDate() ?: return null
        extraNodeLabels[node1] = "Fixing event on $nextFixingDate"
        extraNodeLabels[node2] = "Fixing event on $nextFixingDate"

        val retFuture = SettableFuture.create<Unit>()
        // Complete the future when the state has been consumed on both nodes
        val futA = node1.services.walletService.whenConsumed(theDealRef.ref)
        val futB = node2.services.walletService.whenConsumed(theDealRef.ref)
        Futures.allAsList(futA, futB) success {
            retFuture.set(null)
        } failure { throwable ->
            retFuture.setException(throwable)
        }

        showProgressFor(listOf(node1, node2))
        showConsensusFor(listOf(node1, node2, regulators[0]))

        // For some reason the first fix is always before the effective date.
        if (nextFixingDate > currentDateAndTime.toLocalDate())
            currentDateAndTime = nextFixingDate.atTime(15, 0)

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

        val sessionID = random63BitValue()

        val instigator = TwoPartyDealProtocol.Instigator(node2.info.identity, notary.info.identity, irs, nodeAKey!!, sessionID)
        val acceptor = TwoPartyDealProtocol.Acceptor(node1.info.identity, notary.info.identity, irs, sessionID)

        showProgressFor(listOf(node1, node2))
        showConsensusFor(listOf(node1, node2, regulators[0]))

        val instigatorFuture: ListenableFuture<SignedTransaction> = node1.smm.add("instigator", instigator)

        return Futures.transformAsync(Futures.allAsList(instigatorFuture, node2.smm.add("acceptor", acceptor))) {
            instigatorFuture
        }
    }

    override fun iterate(): InMemoryMessagingNetwork.MessageTransfer? {
        if (executeOnNextIteration.isNotEmpty())
            executeOnNextIteration.removeAt(0)()
        return super.iterate()
    }
}
