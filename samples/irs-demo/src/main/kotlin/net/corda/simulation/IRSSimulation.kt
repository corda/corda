package net.corda.simulation

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.RunOnCallerThread
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flatMap
import net.corda.core.flows.FlowStateMachine
import net.corda.core.map
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.success
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.TwoPartyDealFlow.Acceptor
import net.corda.flows.TwoPartyDealFlow.AutoOffer
import net.corda.flows.TwoPartyDealFlow.Instigator
import net.corda.irs.contract.InterestRateSwap
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.initiateSingleShotFlow
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockIdentityService
import java.time.LocalDate
import java.util.*


/**
 * A simulation in which banks execute interest rate swaps with each other, including the fixing events.
 */
class IRSSimulation(networkSendManuallyPumped: Boolean, runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(networkSendManuallyPumped, runAsync, latencyInjector) {
    val om = net.corda.node.utilities.JsonSupport.createDefaultMapper(MockIdentityService(network.identities))

    init {
        currentDateAndTime = LocalDate.of(2016, 3, 8).atStartOfDay()
    }

    private val executeOnNextIteration = Collections.synchronizedList(LinkedList<() -> Unit>())

    override fun startMainSimulation(): ListenableFuture<Unit> {
        val future = SettableFuture.create<Unit>()

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

    private fun loadLinearHeads(node: SimulatedNode): Map<UniqueIdentifier, StateAndRef<InterestRateSwap.State>> {
        return databaseTransaction(node.database) {
            node.services.vaultService.linearHeadsOfType<InterestRateSwap.State>()
        }
    }

    private fun doNextFixing(i: Int, j: Int): ListenableFuture<Unit>? {
        println("Doing a fixing between $i and $j")
        val node1: SimulatedNode = banks[i]
        val node2: SimulatedNode = banks[j]

        val swaps: Map<UniqueIdentifier, StateAndRef<InterestRateSwap.State>> = loadLinearHeads(node1)
        val theDealRef: StateAndRef<InterestRateSwap.State> = swaps.values.single()

        // Do we have any more days left in this deal's lifetime? If not, return.
        val nextFixingDate = theDealRef.state.data.calculation.nextFixingDate() ?: return null
        extraNodeLabels[node1] = "Fixing event on $nextFixingDate"
        extraNodeLabels[node2] = "Fixing event on $nextFixingDate"

        // Complete the future when the state has been consumed on both nodes
        val futA = node1.services.vaultService.whenConsumed(theDealRef.ref)
        val futB = node2.services.vaultService.whenConsumed(theDealRef.ref)

        showConsensusFor(listOf(node1, node2, regulators[0]))

        // For some reason the first fix is always before the effective date.
        if (nextFixingDate > currentDateAndTime.toLocalDate())
            currentDateAndTime = nextFixingDate.atTime(15, 0)

        return Futures.allAsList(futA, futB).map { Unit }
    }

    private fun startIRSDealBetween(i: Int, j: Int): ListenableFuture<SignedTransaction> {
        val node1: SimulatedNode = banks[i]
        val node2: SimulatedNode = banks[j]

        extraNodeLabels[node1] = "Setting up deal"
        extraNodeLabels[node2] = "Setting up deal"

        // We load the IRS afresh each time because the leg parts of the structure aren't data classes so they don't
        // have the convenient copy() method that'd let us make small adjustments. Instead they're partly mutable.
        // TODO: We should revisit this in post-Excalibur cleanup and fix, e.g. by introducing an interface.
        val irs = om.readValue<InterestRateSwap.State>(javaClass.classLoader.getResource("simulation/trade.json"))
        irs.fixedLeg.fixedRatePayer = node1.info.legalIdentity
        irs.floatingLeg.floatingRatePayer = node2.info.legalIdentity

        @Suppress("UNCHECKED_CAST")
        val acceptorTx = node2.initiateSingleShotFlow(Instigator::class) { Acceptor(it) }.flatMap {
            (it.stateMachine as FlowStateMachine<SignedTransaction>).resultFuture
        }

        showProgressFor(listOf(node1, node2))
        showConsensusFor(listOf(node1, node2, regulators[0]))

        val instigator = Instigator(node2.info.legalIdentity, AutoOffer(notary.info.notaryIdentity, irs), node1.keyPair!!)
        val instigatorTx: ListenableFuture<SignedTransaction> = node1.services.startFlow(instigator).resultFuture

        return Futures.allAsList(instigatorTx, acceptorTx).flatMap { instigatorTx }
    }

    override fun iterate(): InMemoryMessagingNetwork.MessageTransfer? {
        if (executeOnNextIteration.isNotEmpty())
            executeOnNextIteration.removeAt(0)()
        return super.iterate()
    }
}
