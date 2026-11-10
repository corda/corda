package net.corda.nodeapi.internal.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import net.corda.core.internal.telemetry.EndSpanEvent
import net.corda.core.internal.telemetry.EndSpanForFlowEvent
import net.corda.core.internal.telemetry.RecordExceptionEvent
import net.corda.core.internal.telemetry.SetStatusEvent
import net.corda.core.internal.telemetry.StartSpanEvent
import net.corda.core.internal.telemetry.StartSpanForFlowEvent
import net.corda.core.internal.telemetry.TelemetryStatusCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class OpenTelemetryComponentTest {
    private val exporter = RecordingSpanExporter()
    private lateinit var openTelemetry: OpenTelemetrySdk

    @Before
    fun setUp() {
        // OpenTelemetryComponent uses GlobalOpenTelemetry when the Corda OpenTelemetry driver is not installed,
        // as happens when a node runs with the OpenTelemetry Java agent.
        GlobalOpenTelemetry.resetForTest()
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build()
        GlobalOpenTelemetry.set(openTelemetry)
    }

    @After
    fun tearDown() {
        openTelemetry.sdkTracerProvider.close()
        GlobalOpenTelemetry.resetForTest()
    }

    private fun createComponent(spanStartEndEventsEnabled: Boolean = true): OpenTelemetryComponent {
        return OpenTelemetryComponent("test-node", spanStartEndEventsEnabled, copyBaggageToTags = false).also {
            assertThat(it.isEnabled()).isTrue()
        }
    }

    @Test(timeout = 300_000)
    fun `span started by a flow restored from a checkpoint does not fail when start and end events are enabled`() {
        // The component holds its spans only in memory. A flow restored from a checkpoint (after a node restart, say) therefore has
        // no spans in the component, and its fiber has no current span.
        val component = createComponent()
        assertThat(Span.current().spanContext.isValid).isFalse()

        val telemetryId = UUID.randomUUID()
        component.onTelemetryEvent(StartSpanEvent("FlowLogic#sendAllMap", emptyMap(), telemetryId, null))
        component.onTelemetryEvent(EndSpanEvent(telemetryId))

        assertThat(component.spans).isEmpty()
        assertThat(Span.current().spanContext.isValid).describedAs("span scope left open").isFalse()
        val span = exporter.single("FlowLogic#sendAllMap")
        val startEvent = exporter.single("FlowLogic#sendAllMap-start")
        assertThat(startEvent.parentSpanId).isEqualTo(span.spanId)
    }

    @Test(timeout = 300_000)
    fun `span started inside a span from a trace the component does not know does not fail`() {
        val component = createComponent()
        val userSpan = openTelemetry.getTracer("user").spanBuilder("user-span").startSpan()
        val telemetryId = UUID.randomUUID()
        userSpan.makeCurrent().use {
            component.onTelemetryEvent(StartSpanEvent("child", emptyMap(), telemetryId, null))
            component.onTelemetryEvent(EndSpanEvent(telemetryId))
        }
        userSpan.end()

        assertThat(component.spans).isEmpty()
        assertThat(exporter.single("child").parentSpanId).isEqualTo(userSpan.spanContext.spanId)
        assertThat(exporter.single("child-start").parentSpanId).isEqualTo(exporter.single("child").spanId)
    }

    @Test(timeout = 300_000)
    fun `start and end events of a span in a flow are recorded under the flow's start event`() {
        val component = createComponent()
        val flowId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        component.onTelemetryEvent(StartSpanForFlowEvent("TestFlow", emptyMap(), flowId, null, null))
        component.onTelemetryEvent(StartSpanEvent("child", emptyMap(), childId, null))
        component.onTelemetryEvent(EndSpanEvent(childId))
        component.onTelemetryEvent(EndSpanForFlowEvent(flowId))

        assertThat(component.spans).isEmpty()
        val flowStart = exporter.single("TestFlow-start")
        assertThat(exporter.single("child-start").parentSpanId).isEqualTo(flowStart.spanId)
        assertThat(exporter.single("child-end").parentSpanId).isEqualTo(flowStart.spanId)
        assertThat(exporter.single("TestFlow-end").parentSpanId).isEqualTo(exporter.single("Started Events").spanId)
    }

    @Test(timeout = 300_000)
    fun `start event of a span inside a user span in a flow is recorded under the flow's start event`() {
        val component = createComponent()
        val flowId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        component.onTelemetryEvent(StartSpanForFlowEvent("TestFlow", emptyMap(), flowId, null, null))
        val userSpan = openTelemetry.getTracer("user").spanBuilder("user-span").startSpan()
        userSpan.makeCurrent().use {
            component.onTelemetryEvent(StartSpanEvent("child", emptyMap(), childId, null))
            component.onTelemetryEvent(EndSpanEvent(childId))
        }
        userSpan.end()
        component.onTelemetryEvent(EndSpanForFlowEvent(flowId))

        assertThat(component.spans).isEmpty()
        val flowStart = exporter.single("TestFlow-start")
        assertThat(exporter.single("child").parentSpanId).isEqualTo(userSpan.spanContext.spanId)
        assertThat(exporter.single("child-start").parentSpanId).isEqualTo(flowStart.spanId)
    }

    @Test(timeout = 300_000)
    fun `span started by a flow restored from a checkpoint does not fail when start and end events are disabled`() {
        val component = createComponent(spanStartEndEventsEnabled = false)
        val telemetryId = UUID.randomUUID()
        component.onTelemetryEvent(StartSpanEvent("FlowLogic#sendAllMap", emptyMap(), telemetryId, null))
        component.onTelemetryEvent(EndSpanEvent(telemetryId))

        assertThat(component.spans).isEmpty()
        assertThat(exporter.spans.map { it.name }).containsExactly("FlowLogic#sendAllMap")
    }

    @Test(timeout = 300_000)
    fun `other lookups made by a flow restored from a checkpoint do not fail`() {
        // Besides starting spans, a restored flow gets the telemetry context to send when it initiates a session, saves and restores
        // its current span ID when it suspends and resumes, and sets the status of, records exceptions on and ends spans it started
        // before the checkpoint. The component holds none of those spans.
        listOf(true, false).forEach { spanStartEndEventsEnabled ->
            val component = createComponent(spanStartEndEventsEnabled)
            assertThat(Span.current().spanContext.isValid).isFalse()

            val telemetryData = component.getCurrentTelemetryData() as OpenTelemetryContext
            assertThat(telemetryData.context.context).isEmpty()
            assertThat(telemetryData.spanEventChildContext.context).isEmpty()
            assertThat(telemetryData.spanEventParentContext.context).isEmpty()
            assertThat(component.getCurrentTelemetryId()).isEqualTo(UUID(0, 0))

            val idFromCheckpoint = UUID.randomUUID()
            component.setCurrentTelemetryId(idFromCheckpoint)
            component.onTelemetryEvent(SetStatusEvent(idFromCheckpoint, TelemetryStatusCode.ERROR, "failed"))
            component.onTelemetryEvent(RecordExceptionEvent(idFromCheckpoint, IllegalStateException("failed")))
            component.onTelemetryEvent(EndSpanEvent(idFromCheckpoint))
            component.onTelemetryEvent(EndSpanForFlowEvent(idFromCheckpoint))

            assertThat(Span.current().spanContext.isValid).isFalse()
        }
        assertThat(exporter.spans).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `telemetry context taken inside a span from a trace the component does not know does not fail`() {
        val component = createComponent()
        val userSpan = openTelemetry.getTracer("user").spanBuilder("user-span").startSpan()
        val telemetryData = userSpan.makeCurrent().use { component.getCurrentTelemetryData() as OpenTelemetryContext }
        userSpan.end()

        // The user's span is propagated, but there is no start event to propagate with it.
        assertThat(telemetryData.context.context.values).anyMatch { it.contains(userSpan.spanContext.traceId) }
        assertThat(telemetryData.spanEventChildContext.context).isEmpty()
        assertThat(telemetryData.spanEventParentContext.context).isEmpty()
    }

    private class RecordingSpanExporter : SpanExporter {
        val spans = CopyOnWriteArrayList<SpanData>()

        fun single(name: String): SpanData = spans.single { it.name == name }

        override fun export(spans: Collection<SpanData>): CompletableResultCode {
            this.spans += spans
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }
}
