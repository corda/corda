package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.TimedFlow
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.TimedFlowMultiThreadedSMMTests.AbstractTimedFlow.Companion.STEP_1
import net.corda.node.services.TimedFlowMultiThreadedSMMTests.AbstractTimedFlow.Companion.STEP_2
import net.corda.node.services.TimedFlowMultiThreadedSMMTests.AbstractTimedFlow.Companion.STEP_3
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class TimedFlowMultiThreadedSMMTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())

        val requestCount = AtomicInteger(0)
        val invocationCount = AtomicInteger(0)
    }

    @Before
    fun resetCounters() {
        requestCount.set(0)
        invocationCount.set(0)
    }

    @Test
    fun `timed flow is retried`() {
        val user = User("test", "pwd", setOf(Permissions.startFlow<TimedInitiatorFlow>(), Permissions.startFlow<SuperFlow>()))
        driver(DriverParameters(startNodesInProcess = true)) {
            val configOverrides = mapOf("flowTimeout" to mapOf(
                    "timeout" to Duration.ofSeconds(3),
                    "maxRestartCount" to 2,
                    "backoffBase" to 1.0
            ))

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), customOverrides = configOverrides).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use { rpc ->
                whenInvokedDirectly(rpc, nodeBHandle)
                resetCounters()
                whenInvokedAsSubflow(rpc, nodeBHandle)
            }
        }
    }

    @Test
    fun `progress tracker is preserved after flow is retried`() {
        val user = User("test", "pwd", setOf(Permissions.startFlow<TimedInitiatorFlow>(), Permissions.startFlow<SuperFlow>()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val configOverrides = mapOf("flowTimeout" to mapOf(
                    "timeout" to Duration.ofSeconds(2),
                    "maxRestartCount" to 2,
                    "backoffBase" to 1.0
            ))

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), customOverrides = configOverrides).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use { rpc ->
                resetCounters()
                whenInvokedDirectlyAndTracked(rpc, nodeBHandle)
                assertEquals(2, invocationCount.get())
            }
        }
    }


    private fun whenInvokedDirectly(rpc: CordaRPCConnection, nodeBHandle: NodeHandle) {
        rpc.proxy.startFlow(::TimedInitiatorFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        /* The TimedInitiatorFlow is expected to time out the first time, and succeed the second time. */
        assertEquals(2, invocationCount.get())
    }

    private fun whenInvokedAsSubflow(rpc: CordaRPCConnection, nodeBHandle: NodeHandle) {
        rpc.proxy.startFlow(::SuperFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        assertEquals(2, invocationCount.get())
    }

    @StartableByRPC
    class SuperFlow(private val other: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(TimedInitiatorFlow(other))
        }
    }

    private fun whenInvokedDirectlyAndTracked(rpc: CordaRPCConnection, nodeBHandle: NodeHandle) {
        val flowHandle = rpc.proxy.startTrackedFlow(::TimedInitiatorFlow, nodeBHandle.nodeInfo.singleIdentity())

        val stepsCount = 4
        assertEquals(stepsCount, flowHandle.stepsTreeFeed!!.snapshot.size, "Expected progress tracker to return the last step")

        val doneIndex = 3
        val doneIndexStepFromSnapshot = flowHandle.stepsTreeIndexFeed!!.snapshot
        val doneIndexFromUpdates = flowHandle.stepsTreeIndexFeed!!.updates.takeFirst { it == doneIndex }
                .timeout(5, TimeUnit.SECONDS).onErrorResumeNext(rx.Observable.empty()).toBlocking().singleOrDefault(0)
        // we got the last index either via snapshot or update
        assertThat(setOf(doneIndexStepFromSnapshot, doneIndexFromUpdates)).contains(doneIndex).withFailMessage("Expected the last step to be reached")

        val doneLabel = "Done"
        val doneStep = flowHandle.progress.takeFirst { it == doneLabel }
                .timeout(5, TimeUnit.SECONDS).onErrorResumeNext(rx.Observable.empty()).toBlocking().singleOrDefault("")
        assertEquals(doneLabel, doneStep)

        flowHandle.returnValue.getOrThrow()
    }

    /** This abstract class is required to test that the progress tracker gets preserved after restart correctly. */
    abstract class AbstractTimedFlow(override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {
        companion object {
            object STEP_1 : ProgressTracker.Step("Step 1")
            object STEP_2 : ProgressTracker.Step("Step 2")
            object STEP_3 : ProgressTracker.Step("Step 3")

            fun tracker() = ProgressTracker(STEP_1, STEP_2, STEP_3)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class TimedInitiatorFlow(private val other: Party) : AbstractTimedFlow(tracker()), TimedFlow {
        @Suspendable
        override fun call() {
            progressTracker.currentStep = STEP_1
            invocationCount.incrementAndGet()
            progressTracker.currentStep = STEP_2
            val session = initiateFlow(other)
            session.sendAndReceive<Unit>(Unit)
            progressTracker.currentStep = STEP_3
        }
    }

    @InitiatedBy(TimedInitiatorFlow::class)
    class InitiatedFlow(val session: FlowSession) : FlowLogic<Any>() {
        @Suspendable
        override fun call() {
            val value = session.receive<Unit>().unwrap { }
            if (TimedFlowMultiThreadedSMMTests.requestCount.getAndIncrement() == 0) {
                waitForLedgerCommit(SecureHash.randomSHA256())
            } else {
                session.send(value)
            }
        }
    }
}
