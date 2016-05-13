package core.testing

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import contracts.InterestRateSwap
import core.*
import core.contracts.SignedTransaction
import core.contracts.StateAndRef
import core.crypto.SecureHash
import core.node.subsystems.linearHeadsOfType
import core.utilities.JsonSupport
import protocols.TwoPartyDealProtocol
import java.security.KeyPair
import java.time.LocalDate
import java.util.*


/**
 * A simulation in which banks execute interest rate swaps with each other, including the fixing events.
 */
class IRSSimulation(runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(runAsync, latencyInjector) {
    val om = JsonSupport.createDefaultMapper(MockIdentityService(network.identities))

    init {
        currentDay = LocalDate.of(2016, 3, 10)   // Should be 12th but the actual first fixing date gets rolled backwards.
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

        val sideA = TwoPartyDealProtocol.Floater(node2.net.myAddress, sessionID, notary.info, theDealRef, nodeAKey!!, sessionID)
        val sideB = TwoPartyDealProtocol.Fixer(node1.net.myAddress, notary.info.identity, theDealRef, sessionID)

        linkConsensus(listOf(node1, node2, regulators[0]), sideB)
        linkProtocolProgress(node1, sideA)
        linkProtocolProgress(node2, sideB)

        // We have to start the protocols in separate iterations, as adding to the SMM effectively 'iterates' that node
        // in the simulation, so if we don't do this then the two sides seem to act simultaneously.

        val retFuture = SettableFuture.create<Unit>()
        val futA = node1.smm.add("floater", sideA)
        executeOnNextIteration += {
            val futB = node2.smm.add("fixer", sideB)
            Futures.allAsList(futA, futB) success {
                retFuture.set(null)
            } failure { throwable ->
                retFuture.setException(throwable)
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

        val sessionID = random63BitValue()

        val instigator = TwoPartyDealProtocol.Instigator(node2.net.myAddress, notary.info, irs, nodeAKey!!, sessionID)
        val acceptor = TwoPartyDealProtocol.Acceptor(node1.net.myAddress, notary.info.identity, irs, sessionID)

        // TODO: Eliminate the need for linkProtocolProgress
        linkConsensus(listOf(node1, node2, regulators[0]), acceptor)
        linkProtocolProgress(node1, instigator)
        linkProtocolProgress(node2, acceptor)

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