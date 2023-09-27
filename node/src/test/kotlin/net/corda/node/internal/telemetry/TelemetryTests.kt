package net.corda.node.internal.telemetry

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.internal.telemetry.EndSpanEvent
import net.corda.core.internal.telemetry.EndSpanForFlowEvent
import net.corda.core.internal.telemetry.RecordExceptionEvent
import net.corda.core.internal.telemetry.SetStatusEvent
import net.corda.core.internal.telemetry.StartSpanEvent
import net.corda.core.internal.telemetry.StartSpanForFlowEvent
import net.corda.core.internal.telemetry.TelemetryComponent
import net.corda.core.internal.telemetry.TelemetryDataItem
import net.corda.core.internal.telemetry.TelemetryEvent
import net.corda.core.internal.telemetry.TelemetryStatusCode
import net.corda.core.internal.telemetry.telemetryServiceInternal
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentReceiverFlow
import net.corda.node.services.Permissions
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryTests {

    private companion object {
        val cordapps = listOf(enclosedCordapp())
    }

    @Suspendable
    data class TestTelemetryItem(val name: String, val randomUUID: UUID): TelemetryDataItem


    @Test(timeout = 300_000)
    fun `test passing a block with suspend to span func`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {
            val alice = startNode().getOrThrow()
            val handle = alice.rpc.startFlow(TelemetryTests::FlowWithSpanCallAndSleep)
            handle.returnValue.getOrThrow()
            // assertion for test is in Component function setCurrentTelemetryId below.
        }
    }

    @Test(timeout = 300_000)
    fun `run flow with a suspend then check thread locals for fibre are the same`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {
            val alice = startNode().getOrThrow()
            val handle = alice.rpc.startFlow(TelemetryTests::FlowWithSleep)
            handle.returnValue.getOrThrow()
            // assertion for test is in Component function setCurrentTelemetryId below.
        }
    }

    @Test(timeout = 300_000)
    fun `can find 2 distinct telemetry components on node`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {
            val alice = startNode().getOrThrow()
            val telemetryComponentAlice: TestCordaTelemetryComponent = (alice as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java)
            val telemetryComponent2Alice: TestCordaTelemetryComponent2 = alice.services.cordaTelemetryComponent(TestCordaTelemetryComponent2::class.java)
            assertEquals(TestCordaTelemetryComponent::class.java, telemetryComponentAlice::class.java)
            assertEquals(TestCordaTelemetryComponent2::class.java, telemetryComponent2Alice::class.java)
        }
    }

    @Test(timeout = 300_000)
    fun `telemetryId is restored after flow is reloaded from its checkpoint after suspending when reloadCheckpointAfterSuspend is true`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {
            val alice = startNode(
                    providedName = ALICE_NAME,
                    customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)).getOrThrow()
            val telemetryComponentAlice: TestCordaTelemetryComponent = (alice as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java)
            val telemetryComponent2Alice: TestCordaTelemetryComponent2 = alice.services.cordaTelemetryComponent(TestCordaTelemetryComponent2::class.java)
            telemetryComponentAlice.restoredFromCheckpoint = true
            telemetryComponent2Alice.restoredFromCheckpoint = true
            val handle = alice.rpc.startFlow(TelemetryTests::FlowWithSleep)
            handle.returnValue.getOrThrow()
            // assertion for test is in Component function setCurrentTelemetryId below.
        }
    }

    @Test(timeout=300_000)
    fun `run flow and check telemetry components invoked`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS + cordapps, startNodesInProcess = true)) {
            val (nodeA, nodeB) = listOf(startNode(rpcUsers = listOf(user)),
                    startNode())
                    .transpose()
                    .getOrThrow()
            val amount = 1.DOLLARS
            val ref = OpaqueBytes.of(0)
            val recipient = nodeB.nodeInfo.legalIdentities[0]
            nodeA.rpc.startFlow(::CashIssueAndPaymentFlow, amount, ref, recipient, false, defaultNotaryIdentity).returnValue.getOrThrow()
            val flowName = CashIssueAndPaymentFlow::class.java.name
            val receiverFlowName = CashPaymentReceiverFlow::class.java.name

            checkTelemetryComponentCounts(flowName, (nodeA as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java))
            checkTelemetryComponentCounts(flowName, nodeA.services.cordaTelemetryComponent(TestCordaTelemetryComponent2::class.java))

            val nodeBTelemetryComponent = (nodeB as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java)
            val nodeBTelemetryComponent2 = nodeB.services.cordaTelemetryComponent(TestCordaTelemetryComponent2::class.java)

            assertTrue(nodeBTelemetryComponent.endSpanForFlowLatch.await(1, TimeUnit.MINUTES), "Timed out waiting for endSpanForFlow operation on node B")
            assertTrue(nodeBTelemetryComponent2.endSpanForFlowLatch.await(1, TimeUnit.MINUTES), "Timed out waiting for endSpanForFlow operation on node B")

            checkTelemetryComponentCounts(receiverFlowName, nodeB.services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java))
            checkTelemetryComponentCounts(receiverFlowName, nodeB.services.cordaTelemetryComponent(TestCordaTelemetryComponent2::class.java))
        }
    }

    private fun checkTelemetryComponentCounts(flowName: String, telemetryComponent: TelemetryComponentCounts) {
        assertEquals(1, telemetryComponent.startSpanForFlowEvent)
        assertEquals(1, telemetryComponent.endSpanForFlowEvent)
        assertTrue(telemetryComponent.startSpanEvent > 0)
        assertEquals(telemetryComponent.startSpanEvent, telemetryComponent.endSpanEvent)
        assertEquals(flowName, telemetryComponent.startSpanForFlowName)
    }

    @Test(timeout=300_000)
    fun `telemetry data is sent from nodeA to nodeB as part of a flow`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS + cordapps, startNodesInProcess = true)) {
            val (nodeA, nodeB) = listOf(startNode(rpcUsers = listOf(user)),
                    startNode())
                    .transpose()
                    .getOrThrow()
            val amount = 1.DOLLARS
            val ref = OpaqueBytes.of(0)
            val recipient = nodeB.nodeInfo.legalIdentities[0]
            nodeA.rpc.startFlow(::CashIssueAndPaymentFlow, amount, ref, recipient, false, defaultNotaryIdentity).returnValue.getOrThrow()

            val telemetryComponentNodeA = (nodeA as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java)
            val telemetryComponentNodeB = (nodeB as InProcess).services.cordaTelemetryComponent(TestCordaTelemetryComponent::class.java)

            assertNull(telemetryComponentNodeA.retrievedTelemetryDataItem)
            assertEquals(telemetryComponentNodeA.dummyTelemetryItem, telemetryComponentNodeB.retrievedTelemetryDataItem)
        }
    }

    class TestCordaTelemetryComponent: TelemetryComponentCounts() {
        override fun name(): String {
            return "TestCordaTelemetryComponent"
        }
    }

    class TestCordaTelemetryComponent2: TelemetryComponentCounts() {
        override fun name(): String {
            return "TestCordaTelemetryComponent2"
        }
    }

    abstract class TelemetryComponentCounts: TelemetryComponent {
        var restoredFromCheckpoint = false
        val endSpanForFlowLatch = CountDownLatch(1)
        var retrievedTelemetryDataItem: TelemetryDataItem? = null
        var startSpanForFlowEvent = 0
        var endSpanForFlowEvent = 0
        var startSpanEvent = 0
        var endSpanEvent = 0
        var setStatusEvent = 0
        var recordExceptionEvent = 0
        var startSpanForFlowName: String? = null
        var startSpanName: String? = null
        var spanTelemetryIds = ArrayDeque<UUID>()
        val currentUUID = ThreadLocal<UUID>()
        var previousTelemetryId: UUID? = null
        val dummyTelemetryItem = TestTelemetryItem("this is a dummy string", UUID.randomUUID())

        override fun getCurrentSpanId(): String {
            TODO("Not yet implemented")
        }

        override fun getCurrentTraceId(): String {
            TODO("Not yet implemented")
        }

        override fun getCurrentBaggage(): Map<String, String> {
            TODO("Not yet implemented")
        }

        override fun isEnabled(): Boolean {
            return true
        }

        override fun setCurrentTelemetryId(id: UUID) {
            if (!restoredFromCheckpoint) {
                // If we have not been restored from a checkpoint then the threadlocal should be the same
                // as it was, so check this.
                // If we have been restored from a checkpoint then the threadlocal will be null. So skip this step if we have.
                assertEquals(currentUUID.get(), id)
            }
            // Here threadlocal will be null as we have been check pointed.
            // Check the uuid passed to us is the same as the one we returned earlier.
            assertEquals(previousTelemetryId, id)
            currentUUID.set(id)
        }

        override fun getCurrentTelemetryId(): UUID {
            val uuid = currentUUID.get() ?: UUID(0, 0)
            // Store the uuid we return so can check for same value after the checkpoint
            previousTelemetryId = uuid
            return uuid
        }

        override fun onTelemetryEvent(event: TelemetryEvent) {
            when (event) {
                is StartSpanForFlowEvent -> {
                    startSpanForFlowEvent++
                    startSpanForFlow(event.name, event.attributes, event.telemetryId, event.flowLogic, event.telemetryDataItem)
                }
                is EndSpanForFlowEvent -> {
                    endSpanForFlowEvent++
                    endSpanForFlow(event.telemetryId)
                    endSpanForFlowLatch.countDown()
                }
                is StartSpanEvent -> {
                    startSpanEvent++
                    startSpan(event.name, event.attributes, event.telemetryId, event.flowLogic)
                }
                is EndSpanEvent -> {
                    endSpanEvent++
                    endSpan(event.telemetryId)
                }
                is SetStatusEvent -> {
                    setStatusEvent++
                    setStatus(event.telemetryId, event.telemetryStatusCode, event.message)
                }
                is RecordExceptionEvent -> {
                    recordExceptionEvent++
                    recordException(event.telemetryId, event.throwable)
                }
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?, telemetryDataItem: TelemetryDataItem?) {
            retrievedTelemetryDataItem = telemetryDataItem
            startSpanForFlowName = name
            spanTelemetryIds.push(telemetryId)
            currentUUID.set(telemetryId)
        }

        fun endSpanForFlow(telemetryId: UUID) {
            assertEquals(spanTelemetryIds.pop(), telemetryId)
            val newUUID = spanTelemetryIds.peek() ?: UUID(0, 0)
            currentUUID.set(newUUID)
        }

        @Suppress("UNUSED_PARAMETER")
        fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
            startSpanName = name
            spanTelemetryIds.push(telemetryId)
            currentUUID.set(telemetryId)
        }

        fun endSpan(telemetryId: UUID) {
            assertEquals(spanTelemetryIds.pop(), telemetryId)
            val newUUID = spanTelemetryIds.peek() ?: UUID(0, 0)
            currentUUID.set(newUUID)
        }

        override fun getCurrentTelemetryData(): TelemetryDataItem {
            return dummyTelemetryItem
        }

        @Suppress("UNUSED_PARAMETER")
        fun setStatus(telemetryId: UUID, statusCode: TelemetryStatusCode, message: String) {
        }

        @Suppress("UNUSED_PARAMETER")
        fun recordException(telemetryId: UUID, throwable: Throwable) {
        }

        override fun getTelemetryHandles(): List<Any> {
            return emptyList()
        }
    }


    @StartableByRPC
    class FlowWithSleep : FlowLogic<InvocationContext>() {
        companion object {
            object TESTSTEP : ProgressTracker.Step("Custom progress step")
        }
        override val progressTracker: ProgressTracker = ProgressTracker(TESTSTEP)

        @Suspendable
        override fun call(): InvocationContext {
            // Do a sleep which invokes a suspend
            sleep(Duration.ofSeconds(1))
            progressTracker.currentStep = TESTSTEP
            return stateMachine.context
        }
    }

    @StartableByRPC
    class FlowWithSpanCallAndSleep : FlowLogic<InvocationContext>() {
        companion object {
            object TESTSTEP : ProgressTracker.Step("Custom progress step")
        }
        override val progressTracker: ProgressTracker = ProgressTracker(TESTSTEP)

        @Suspendable
        override fun call(): InvocationContext {
            return serviceHub.telemetryServiceInternal.span(this::class.java.name, emptyMap(), this) {
                // Do a sleep which invokes a suspend
                sleep(Duration.ofSeconds(1))
                progressTracker.currentStep = TESTSTEP
                stateMachine.context
            }
        }
    }
}