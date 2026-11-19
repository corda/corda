package net.corda.node.internal.telemetry

import co.paralleluniverse.fibers.Suspendable
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.telemetry.ComponentTelemetryIds
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.config.TelemetryConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
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
import java.time.Duration
import java.time.Instant

/**
 * When a flow suspends, it saves the current telemetry ID of each telemetry component the node has, as [ComponentTelemetryIds] in
 * its checkpoint. When it resumes, the node restores an ID for each component it has then. These tests restart a node with a
 * different set of telemetry components while a flow is suspended, so the restored flow has no saved ID for a component that was
 * enabled at the restart. Before the fix, such a flow failed with a NullPointerException as soon as it resumed.
 */
class TelemetryComponentsChangedOnRestoreTests {
    private lateinit var openTelemetry: OpenTelemetrySdk
    private lateinit var mockNet: InternalMockNetwork
    private val nodesToCloseDbFor = mutableListOf<TestStartedNode>()

    @Before
    fun setUp() {
        // The node's OpenTelemetryComponent uses GlobalOpenTelemetry when the Corda OpenTelemetry driver is not installed. It needs a
        // span processor, otherwise the SDK hands out a no-op tracer and the node does not enable the component.
        GlobalOpenTelemetry.resetForTest()
        val exporter = object : SpanExporter {
            override fun export(spans: Collection<SpanData>): CompletableResultCode = CompletableResultCode.ofSuccess()
            override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
            override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build()
        GlobalOpenTelemetry.set(openTelemetry)
        mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(enclosedCordapp()))
    }

    @After
    fun tearDown() {
        try {
            nodesToCloseDbFor.forEach { it.internals.manuallyCloseDB() }
            mockNet.close()
        } finally {
            openTelemetry.sdkTracerProvider.close()
            GlobalOpenTelemetry.resetForTest()
        }
    }

    // Each call creates new parameters, so each node gets its own flow manager.
    private fun nodeParameters(
            openTelemetryEnabled: Boolean,
            simpleLogTelemetryEnabled: Boolean,
            legalName: CordaX500Name? = null
    ) = InternalMockNodeParameters(
            legalName = legalName,
            configOverrides = {
                doReturn(TelemetryConfiguration(
                        openTelemetryEnabled = openTelemetryEnabled,
                        simpleLogTelemetryEnabled = simpleLogTelemetryEnabled,
                        spanStartEndEventsEnabled = false,
                        copyBaggageToTags = false
                )).whenever(it).telemetry
            }
    )

    private fun TestStartedNode.hasOpenTelemetry() = services.telemetryService.getTelemetryHandle(OpenTelemetry::class.java) != null

    private fun TestStartedNode.waitUntilSuspendedInReceive(flowId: StateMachineRunId) {
        val deadline = Instant.now() + Duration.ofSeconds(60)
        val receive = FlowIORequest.Receive::class.java.simpleName
        while (database.transaction { internals.checkpointStorage.getCheckpoint(flowId)?.flowIoRequest } != receive) {
            check(Instant.now() < deadline) { "Flow $flowId did not suspend waiting to receive" }
            Thread.sleep(10)
        }
    }

    /**
     * Starts a flow on Bob's node, configured with [openTelemetryBefore] and [simpleLogBefore], and restarts the node with
     * [openTelemetryAfter] and [simpleLogAfter] while the flow is suspended with a checkpoint. Then checks the restored flow completes.
     */
    private fun restoredFlowCompletes(
            openTelemetryBefore: Boolean,
            simpleLogBefore: Boolean,
            openTelemetryAfter: Boolean,
            simpleLogAfter: Boolean
    ) {
        val aliceNode = mockNet.createNode(nodeParameters(openTelemetryEnabled = true, simpleLogTelemetryEnabled = true, legalName = ALICE_NAME))
        val bobNode = mockNet.createNode(nodeParameters(openTelemetryBefore, simpleLogBefore, BOB_NAME))
        assertThat(bobNode.hasOpenTelemetry()).isEqualTo(openTelemetryBefore)

        // The flow initiates a session with Alice and suspends, with a checkpoint, waiting for her reply. The network is not run,
        // so Alice's responder has not started yet.
        val flowId = bobNode.services.startFlow(ReceiveThenSendFlow(aliceNode.info.singleIdentity())).id
        bobNode.waitUntilSuspendedInReceive(flowId)
        bobNode.internals.acceptableLiveFiberCountOnStop = 1
        nodesToCloseDbFor += bobNode
        val restartedBobNode = mockNet.restartNode(bobNode, nodeParameters(openTelemetryAfter, simpleLogAfter))
        assertThat(restartedBobNode.hasOpenTelemetry()).isEqualTo(openTelemetryAfter)
        val (_, result) = restartedBobNode.smm.findStateMachines(ReceiveThenSendFlow::class.java).single()
        mockNet.runNetwork()

        assertThat(result.getOrThrow(Duration.ofSeconds(60))).isEqualTo("Hello")
    }

    @Test(timeout = 300_000)
    fun `flow restored after the node is restarted with OpenTelemetry enabled completes`() {
        restoredFlowCompletes(openTelemetryBefore = false, simpleLogBefore = true, openTelemetryAfter = true, simpleLogAfter = true)
    }

    @Test(timeout = 300_000)
    fun `flow restored after the node is restarted with simple log telemetry enabled completes`() {
        restoredFlowCompletes(openTelemetryBefore = true, simpleLogBefore = false, openTelemetryAfter = true, simpleLogAfter = true)
    }

    @Test(timeout = 300_000)
    fun `flow restored after the node is restarted with a telemetry component disabled completes`() {
        restoredFlowCompletes(openTelemetryBefore = true, simpleLogBefore = true, openTelemetryAfter = true, simpleLogAfter = false)
    }

    @InitiatingFlow
    class ReceiveThenSendFlow(private val counterparty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val session = initiateFlow(counterparty)
            val greeting = session.receive<String>().unwrap { it }
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
}
