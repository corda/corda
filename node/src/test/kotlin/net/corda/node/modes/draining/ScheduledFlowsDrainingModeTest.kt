/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.jvmName
import kotlin.test.fail

class ScheduledFlowsDrainingModeTest {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: StartedNode
    private lateinit var bobNode: StartedNode
    private lateinit var notary: Party
    private lateinit var alice: Party
    private lateinit var bob: Party

    private var executor: ScheduledExecutorService? = null

    companion object {
        private val logger = loggerFor<ScheduledFlowsDrainingModeTest>()
    }

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.testing.contracts"), threadPerNode = true)
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        executor = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        executor!!.shutdown()
    }

    @Test
    fun `flows draining mode ignores scheduled flows until unset`() {

        val latch = CountDownLatch(1)
        var shouldFail = true

        aliceNode.services.nodeProperties.flowsDrainingMode.setEnabled(true)
        val scheduledStates = aliceNode.services
                .vaultService
                .updates
                .filter { update -> update.containsType<ScheduledState>() }
                .map { update -> update.produced.single().state.data as ScheduledState }

        scheduledStates.filter { state -> !state.processed }.doOnNext { _ ->
            // this is needed because there is a delay between the moment a SchedulableState gets in the Vault and the first time nextScheduledActivity is called
            executor!!.schedule({
                logger.info("Disabling flows draining mode")
                shouldFail = false
                aliceNode.services.nodeProperties.flowsDrainingMode.setEnabled(false)
            }, 5, TimeUnit.SECONDS)
        }.subscribe()

        scheduledStates.filter { state -> state.processed }.doOnNext { _ ->
            if (shouldFail) {
                fail("Should not have happened before draining is switched off.")
            }
            latch.countDown()
        }.subscribe()

        val flow = aliceNode.services.startFlow(InsertInitialStateFlow(bob, notary))

        flow.resultFuture.getOrThrow()
        mockNet.waitQuiescent()

        latch.await()
    }

    data class ScheduledState(private val creationTime: Instant, val source: Party, val destination: Party, val processed: Boolean = false, override val linearId: UniqueIdentifier = UniqueIdentifier()) : SchedulableState, LinearState {

        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            return if (!processed) {
                val logicRef = flowLogicRefFactory.create(ScheduledFlow::class.jvmName, thisStateRef)
                ScheduledActivity(logicRef, creationTime)
            } else {
                null
            }
        }

        override val participants: List<Party> get() = listOf(source, destination)
    }

    class InsertInitialStateFlow(private val destination: Party, private val notary: Party) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val scheduledState = ScheduledState(serviceHub.clock.instant(), ourIdentity, destination)
            val builder = TransactionBuilder(notary).addOutputState(scheduledState, DummyContract.PROGRAM_ID).addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx))
        }
    }

    @SchedulableFlow
    class ScheduledFlow(private val stateRef: StateRef) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            val state = serviceHub.toStateAndRef<ScheduledState>(stateRef)
            val scheduledState = state.state.data
            // Only run flow over states originating on this node
            if (!serviceHub.myInfo.isLegalIdentity(scheduledState.source)) {
                return
            }
            require(!scheduledState.processed) { "State should not have been previously processed" }
            val notary = state.state.notary
            val newStateOutput = scheduledState.copy(processed = true)
            val builder = TransactionBuilder(notary).addInputState(state).addOutputState(newStateOutput, DummyContract.PROGRAM_ID).addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, setOf(scheduledState.destination)))
        }
    }
}