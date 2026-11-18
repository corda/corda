package net.corda.node.internal.telemetry

import co.paralleluniverse.fibers.Suspendable
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowIORequest
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.config.TelemetryConfiguration
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.nodeapi.internal.telemetry.OpenTelemetryComponent
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A flow restored from a checkpoint (after a node restart, or when the flow hospital retries it) starts with no current span, and
 * [OpenTelemetryComponent] holds no spans for it, because the component keeps its spans only in memory. These tests check that such a
 * flow can carry on starting spans, in particular with spanStartEndEventsEnabled = true (CS-4300).
 */
class OpenTelemetryRestoredFlowTests {
    private val exporter = RecordingSpanExporter()
    private lateinit var openTelemetry: OpenTelemetrySdk
    private lateinit var mockNet: InternalMockNetwork
    private val nodesToCloseDbFor = mutableListOf<TestStartedNode>()

    @Before
    fun setUp() {
        // The node's OpenTelemetryComponent uses GlobalOpenTelemetry when the Corda OpenTelemetry driver is not installed,
        // as happens when a node runs with the OpenTelemetry Java agent.
        GlobalOpenTelemetry.resetForTest()
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build()
        GlobalOpenTelemetry.set(openTelemetry)
        mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()))
        RetryOnceAfterReceiveFlow.retried = false
    }

    @After
    fun tearDown() {
        try {
            nodesToCloseDbFor.forEach { it.internals.manuallyCloseDB() }
            mockNet.close()
        } finally {
            StaffedFlowHospital.DatabaseEndocrinologist.customConditions.clear()
            openTelemetry.sdkTracerProvider.close()
            GlobalOpenTelemetry.resetForTest()
        }
    }

    // Each call creates new parameters, so each node gets its own flow manager.
    private fun nodeParameters(spanStartEndEventsEnabled: Boolean, legalName: CordaX500Name? = null) = InternalMockNodeParameters(
            legalName = legalName,
            configOverrides = {
                doReturn(TelemetryConfiguration(
                        openTelemetryEnabled = true,
                        simpleLogTelemetryEnabled = false,
                        spanStartEndEventsEnabled = spanStartEndEventsEnabled,
                        copyBaggageToTags = false
                )).whenever(it).telemetry
            }
    )

    private fun createNodes(spanStartEndEventsEnabled: Boolean): Pair<TestStartedNode, TestStartedNode> {
        val aliceNode = mockNet.createNode(nodeParameters(spanStartEndEventsEnabled, ALICE_NAME))
        val bobNode = mockNet.createNode(nodeParameters(spanStartEndEventsEnabled, BOB_NAME))
        assertThat(bobNode.services.telemetryService.getTelemetryHandle(io.opentelemetry.api.OpenTelemetry::class.java))
                .describedAs("OpenTelemetry component not enabled on the node")
                .isNotNull
        return Pair(aliceNode, bobNode)
    }

    /**
     * Waits until [flowId] has written a checkpoint while suspended waiting to receive from the counterparty, then stops [node] and starts
     * it again on the same database, so that it restores the flow from that checkpoint rather than running it again from the start.
     */
    private fun restartWhenSuspendedInReceive(
            node: TestStartedNode,
            flowId: StateMachineRunId,
            spanStartEndEventsEnabled: Boolean
    ): TestStartedNode {
        val receiveRequests = setOf(FlowIORequest.Receive::class.java.simpleName, FlowIORequest.SendAndReceive::class.java.simpleName)
        val deadline = Instant.now() + Duration.ofSeconds(60)
        while (node.database.transaction { node.internals.checkpointStorage.getCheckpoint(flowId)?.flowIoRequest } !in receiveRequests) {
            check(Instant.now() < deadline) { "Flow $flowId did not suspend waiting to receive" }
            Thread.sleep(10)
        }
        node.internals.acceptableLiveFiberCountOnStop = 1
        nodesToCloseDbFor += node
        return mockNet.restartNode(node, nodeParameters(spanStartEndEventsEnabled))
    }

    private fun restoredFlowStartsSpans(spanStartEndEventsEnabled: Boolean) {
        val (aliceNode, bobNode) = createNodes(spanStartEndEventsEnabled)
        // The flow initiates a session with Alice and suspends, with a checkpoint, waiting for her reply. The network is not run,
        // so Alice's responder has not started yet.
        val flowId = bobNode.services.startFlow(ReceiveThenSendFlow(aliceNode.info.singleIdentity())).id

        val restartedBobNode = restartWhenSuspendedInReceive(bobNode, flowId, spanStartEndEventsEnabled)
        val (_, result) = restartedBobNode.smm.findStateMachines(ReceiveThenSendFlow::class.java).single()
        mockNet.runNetwork()

        assertThat(result.getOrThrow()).isEqualTo("Hello")
        assertThat(exporter.names()).contains(NoOpSubFlow::class.java.name)
        assertThat(exporter.names()).anyMatch { it.endsWith("FlowSessionImpl#sendAndReceive") }
    }

    @Test(timeout = 300_000)
    fun `flow restored from a checkpoint after a restart can start spans when span start and end events are enabled`() {
        restoredFlowStartsSpans(spanStartEndEventsEnabled = true)
        assertThat(exporter.names()).contains("${NoOpSubFlow::class.java.name}-start")
    }

    @Test(timeout = 300_000)
    fun `flow restored from a checkpoint after a restart can start spans when span start and end events are disabled`() {
        restoredFlowStartsSpans(spanStartEndEventsEnabled = false)
        assertThat(exporter.names()).noneMatch { it.endsWith("-start") || it.endsWith("-end") }
    }

    @Test(timeout = 300_000)
    fun `flow restored after a restart while collecting signatures completes when span start and end events are enabled`() {
        val (aliceNode, bobNode) = createNodes(spanStartEndEventsEnabled = true)
        val alice = aliceNode.info.singleIdentity()
        // As in CS-4300: the flow is suspended inside CollectSignaturesFlow, waiting on the counterparty, when the node stops.
        val flowId = bobNode.services.startFlow(CollectSignatureFromCounterpartyFlow(alice)).id

        val restartedBobNode = restartWhenSuspendedInReceive(bobNode, flowId, spanStartEndEventsEnabled = true)
        val (_, result) = restartedBobNode.smm.findStateMachines(CollectSignatureFromCounterpartyFlow::class.java).single()
        mockNet.runNetwork()

        val stx = result.getOrThrow() as SignedTransaction
        assertThat(stx.sigs.map { it.by }).contains(alice.owningKey, restartedBobNode.info.singleIdentity().owningKey)
        assertResponderFinished(aliceNode)
    }

    // A responder finishes on its own flow thread, possibly just after the initiating flow has its result.
    private fun assertResponderFinished(node: TestStartedNode) {
        val deadline = Instant.now() + Duration.ofSeconds(30)
        while (node.smm.allStateMachines.isNotEmpty() && Instant.now() < deadline) {
            Thread.sleep(10)
        }
        assertThat(node.smm.allStateMachines).describedAs("The responder is still running").isEmpty()
    }

    private fun restoredFlowInitiatesSession(spanStartEndEventsEnabled: Boolean) {
        val (aliceNode, bobNode) = createNodes(spanStartEndEventsEnabled)
        val flowId = bobNode.services.startFlow(InitiateAfterRestoreFlow(aliceNode.info.singleIdentity())).id

        val restartedBobNode = restartWhenSuspendedInReceive(bobNode, flowId, spanStartEndEventsEnabled)
        val (_, result) = restartedBobNode.smm.findStateMachines(InitiateAfterRestoreFlow::class.java).single()
        mockNet.runNetwork()

        assertThat(result.getOrThrow()).isEqualTo(listOf("reply-1", "reply-2"))
        assertResponderFinished(aliceNode)
        // The second responder started its flow span from the telemetry context the restored flow sent.
        assertThat(exporter.names().filter { it == InitiateAfterRestoreResponder::class.java.name }).hasSize(2)
    }

    @Test(timeout = 300_000)
    fun `flow restored after a restart can initiate a session when span start and end events are enabled`() {
        restoredFlowInitiatesSession(spanStartEndEventsEnabled = true)
    }

    @Test(timeout = 300_000)
    fun `flow restored after a restart can initiate a session when span start and end events are disabled`() {
        restoredFlowInitiatesSession(spanStartEndEventsEnabled = false)
    }

    @Test(timeout = 300_000)
    fun `flow retried by the flow hospital from its checkpoint can start spans when span start and end events are enabled`() {
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add { it is RetryCausingError }
        val (aliceNode, bobNode) = createNodes(spanStartEndEventsEnabled = true)
        val handle = bobNode.services.startFlow(RetryOnceAfterReceiveFlow(aliceNode.info.singleIdentity()))
        mockNet.runNetwork()

        assertThat(handle.resultFuture.getOrThrow()).isEqualTo("Hello")
        assertThat(RetryOnceAfterReceiveFlow.retried).isTrue()
    }

    @InitiatingFlow
    class ReceiveThenSendFlow(private val counterparty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val session = initiateFlow(counterparty)
            val greeting = session.receive<String>().unwrap { it }
            // These are the first spans the flow starts after it has been restored from its checkpoint.
            subFlow(NoOpSubFlow())
            session.sendAndReceive<String>("ack").unwrap { check(it == "done") }
            return greeting
        }
    }

    @InitiatedBy(ReceiveThenSendFlow::class)
    class ReceiveThenSendResponder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.send("Hello")
            session.receive<String>().unwrap { check(it == "ack") }
            session.send("done")
        }
    }

    class NoOpSubFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }

    @InitiatingFlow
    class InitiateAfterRestoreFlow(private val counterparty: Party) : FlowLogic<List<String>>() {
        @Suspendable
        override fun call(): List<String> {
            val first = initiateFlow(counterparty).sendAndReceive<String>(1).unwrap { it }
            // The flow is restored here. It has no current span, so it sends the new session a telemetry context with no span in it,
            // and the counterparty starts its flow span from that.
            val second = initiateFlow(counterparty).sendAndReceive<String>(2).unwrap { it }
            return listOf(first, second)
        }
    }

    @InitiatedBy(InitiateAfterRestoreFlow::class)
    class InitiateAfterRestoreResponder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val number = session.receive<Int>().unwrap { it }
            session.send("reply-$number")
        }
    }

    @InitiatingFlow
    class CollectSignatureFromCounterpartyFlow(private val counterparty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = DummyContract.generateInitial(1, notary, ourIdentity.ref(1), counterparty.ref(1))
            val partiallySignedTx = serviceHub.signInitialTransaction(builder)
            val session = initiateFlow(counterparty)
            return subFlow(CollectSignaturesFlow(partiallySignedTx, listOf(session)))
        }
    }

    @InitiatedBy(CollectSignatureFromCounterpartyFlow::class)
    class CollectSignatureResponder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(AcceptingSignTransactionFlow(session))
        }
    }

    class AcceptingSignTransactionFlow(session: FlowSession) : SignTransactionFlow(session) {
        override fun checkTransaction(stx: SignedTransaction) = Unit
    }

    class RetryCausingError : SQLException("deadlock")

    @InitiatingFlow
    class RetryOnceAfterReceiveFlow(private val counterparty: Party) : FlowLogic<String>() {
        companion object {
            @Volatile
            var retried = false
        }

        @Suspendable
        override fun call(): String {
            val session = initiateFlow(counterparty)
            val greeting = session.receive<String>().unwrap { it }
            if (!retried) {
                retried = true
                // The flow hospital retries the flow from its last checkpoint, which was written when the flow suspended in receive.
                throw RetryCausingError()
            }
            subFlow(NoOpSubFlow())
            session.sendAndReceive<String>("ack").unwrap { check(it == "done") }
            return greeting
        }
    }

    @InitiatedBy(RetryOnceAfterReceiveFlow::class)
    class RetryOnceAfterReceiveResponder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.send("Hello")
            session.receive<String>().unwrap { check(it == "ack") }
            session.send("done")
        }
    }

    private class RecordingSpanExporter : SpanExporter {
        private val spans = CopyOnWriteArrayList<SpanData>()

        fun names(): List<String> = spans.map { it.name }

        override fun export(spans: Collection<SpanData>): CompletableResultCode {
            this.spans += spans
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }
}
