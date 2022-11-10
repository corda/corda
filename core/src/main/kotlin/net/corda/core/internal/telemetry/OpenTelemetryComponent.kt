package net.corda.core.internal.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import net.corda.opentelemetrydriver.OpenTelemetryDriver

const val TRACEIDHEX_LENGTH = 32
const val SPANIDHEX_LENGTH = 16
@CordaSerializable
data class SerializableSpanContext(val traceIdHex : String, val spanIdHex : String, val traceFlagsHex : String, val traceStateMap : Map<String, String>) {

    constructor(spanContext: SpanContext) : this(spanContext.traceId, spanContext.spanId, spanContext.traceFlags.asHex(), spanContext.traceState.asMap().toMap())

    constructor() : this("0".repeat(TRACEIDHEX_LENGTH), "0".repeat(SPANIDHEX_LENGTH), TraceFlags.getDefault().asHex(), TraceState.getDefault().asMap())

    fun createSpanContext() = SpanContext.create(traceIdHex, spanIdHex, TraceFlags.fromHex(traceFlagsHex, 0), createTraceState())

    fun createRemoteSpanContext() = SpanContext.createFromRemoteParent(traceIdHex, spanIdHex, TraceFlags.fromHex(traceFlagsHex, 0), createTraceState())

    private fun createTraceState() = traceStateMap.toList().fold(TraceState.builder()) { builder, pair -> builder.put(pair.first, pair.second)}.build()
}

data class OpenTelemetryContext(val spanContext: SerializableSpanContext, val startedSpanContext: SerializableSpanContext, val baggage: Map<String,String>): TelemetryDataItem

data class SpanInfo(val name: String, val span: Span, val spanScope: Scope, val spanEventContext: SerializableSpanContext? = null, val parentSpanEventContext: SerializableSpanContext? = null)

class TracerSetup(serviceName: String) {
    private var openTelemetryDriver: Any? = null
    val openTelemetry: OpenTelemetry by lazy {
        try {
            openTelemetryDriver = OpenTelemetryDriver()
            (openTelemetryDriver as OpenTelemetryDriver).getOpenTelemetry(serviceName)
        }
        catch (ex: NoClassDefFoundError) {
            GlobalOpenTelemetry.get()
        }
    }
    fun getTracer(): Tracer {
        return openTelemetry.tracerProvider.get(OpenTelemetryComponent::class.java.name)
    }

    fun shutdown() {
        (openTelemetryDriver as? OpenTelemetryDriver)?.shutdown()
    }
}

@Suppress("TooManyFunctions")
class OpenTelemetryComponent(val serviceName: String, val spanStartEndEventsEnabled: Boolean) : TelemetryComponent {
    val tracerSetup = TracerSetup(serviceName)
    val tracer: Tracer = tracerSetup.getTracer()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OpenTelemetryComponent::class.java)
        const val OPENTELEMETRY_COMPONENT_NAME = "OpenTelemetry"
    }

    val rootSpans = ConcurrentHashMap<UUID, SpanInfo>()
    val spans = ConcurrentHashMap<UUID, SpanInfo>()
    val baggages = ConcurrentHashMap<UUID, Scope>()

    override fun isEnabled(): Boolean {
        // DefaultTracer is the NoOp tracer in the OT API
        return tracerSetup.getTracer().javaClass.name != "io.opentelemetry.api.trace.DefaultTracer"
    }

    override fun name(): String = OPENTELEMETRY_COMPONENT_NAME
    override fun onTelemetryEvent(event: TelemetryEvent) {
        when (event) {
            is StartSpanForFlowEvent -> startSpanForFlow(event.name, event.attributes, event.telemetryId, event.flowLogic, event.telemetryDataItem)
            is EndSpanForFlowEvent -> endSpanForFlow(event.telemetryId)
            is StartSpanEvent -> startSpan(event.name, event.attributes, event.telemetryId, event.flowLogic)
            is EndSpanEvent -> endSpan(event.telemetryId)
            is SetStatusEvent -> setStatus(event.telemetryId, event.telemetryStatusCode, event.message)
            is RecordExceptionEvent -> recordException(event.telemetryId, event.throwable)
            is ShutdownTelemetryEvent -> shutdownTelemetry()
        }
    }

    private fun shutdownTelemetry() {
        tracerSetup.shutdown()
    }

    @Suppress("LongParameterList")
    private fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?,
                                 telemetryDataItem: TelemetryDataItem?) {

        val baggageAttributes = (telemetryDataItem as? OpenTelemetryContext)?.baggage?.let {
            val baggageBuilder = it.toList().fold(Baggage.current().toBuilder()) {builder, attribute -> builder.put(attribute.first, attribute.second)}
            baggages[telemetryId] = baggageBuilder.build().makeCurrent()
            it
        } ?: emptyMap()

        // Also add any baggage to the span
        val attributesMap = (attributes+baggageAttributes).toList()
                .fold(Attributes.builder()) { builder, attribute -> builder.put(attribute.first, attribute.second) }.also {
                    populateWithFlowAttributes(it, flowLogic)
                }.build()
        if (telemetryDataItem != null) {
            startSpanForFlowWithRemoteParent(name, attributesMap, telemetryId, telemetryDataItem)
        }
        else {
            startSpanForFlowWithNoParent(name, attributesMap, telemetryId)
        }
    }

    private fun startSpanForFlowWithNoParent(name: String, attributesMap: Attributes, telemetryId: UUID) {
        val rootSpan = tracer.spanBuilder(name).setAllAttributes(attributesMap).setAllAttributes(Attributes.of(AttributeKey.stringKey("root.flow"), "true")).startSpan()
        val rootSpanScope = rootSpan.makeCurrent()
        if (spanStartEndEventsEnabled) {
            val startedSpanContexts = createSpanToCaptureStartedSpanEvent(name, rootSpan, attributesMap)
            val span = tracer.spanBuilder("Child Spans").setParent(Context.current().with(rootSpan)).startSpan()
            val spanScope = span.makeCurrent()
            rootSpans[telemetryId] = SpanInfo(name, rootSpan, rootSpanScope)
            spans[telemetryId] = SpanInfo(name, span, spanScope, startedSpanContexts.first, startedSpanContexts.second)
        }
        else {
            spans[telemetryId] = SpanInfo(name, rootSpan, rootSpanScope)
        }
    }

    private fun startSpanForFlowWithRemoteParent(name: String, attributesMap: Attributes, telemetryId: UUID, telemetryDataItem: TelemetryDataItem) {
        val parentContext = (telemetryDataItem as OpenTelemetryContext).spanContext
        val spanContext = parentContext.createRemoteSpanContext()
        val parentSpan = Span.wrap(spanContext)
        val span = tracer.spanBuilder(name).setParent(Context.current().with(parentSpan)).setAllAttributes(attributesMap).startSpan()
        val spanScope = span.makeCurrent()
        if (spanStartEndEventsEnabled) {
            val contexts = createSpanToCaptureStartedSpanEventWithRemoteParent(name, telemetryDataItem, attributesMap)
            spans[telemetryId] = SpanInfo(name, span, spanScope, contexts.first, contexts.second)
        }
        else {
            spans[telemetryId] = SpanInfo(name, span, spanScope)
        }
    }

    private fun createSpanToCaptureStartedSpanEvent(name: String, rootSpan: Span, attributesMap: Attributes): Pair<SerializableSpanContext, SerializableSpanContext> {
        val startedSpan = tracer.spanBuilder("Started Events").setAllAttributes(attributesMap).setAllAttributes(Attributes.of(AttributeKey.stringKey("root.startend.events"), "true")).setParent(Context.current().with(rootSpan)).startSpan()
        val serializableSpanContext = SerializableSpanContext(startedSpan.spanContext)
        startedSpan.end()
        val startedSpanContext = serializableSpanContext.createSpanContext()
        val startedSpanFromContext = Span.wrap(startedSpanContext)
        val startedSpanChild = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                .setParent(Context.current().with(startedSpanFromContext)).startSpan()
        val childSerializableSpanContext = SerializableSpanContext(startedSpanChild.spanContext)
        startedSpanChild.end()
        return Pair(childSerializableSpanContext, serializableSpanContext)
    }

    private fun createSpanToCaptureStartedSpanEventWithRemoteParent(name: String, telemetryDataItem: OpenTelemetryContext, attributesMap: Attributes ): Pair<SerializableSpanContext, SerializableSpanContext> {
        val startedSpanParentContext = telemetryDataItem.startedSpanContext
        val startedSpanContext = startedSpanParentContext.createRemoteSpanContext()
        val startedSpanFromContext = Span.wrap(startedSpanContext)
        val startedSpanChild  = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                .setParent(Context.current().with(startedSpanFromContext)).startSpan()
        val serializableSpanContext = SerializableSpanContext(startedSpanChild.spanContext)
        startedSpanChild.end()
        return Pair(serializableSpanContext, startedSpanParentContext)
    }

    private fun endSpanForFlow(telemetryId: UUID){
        val spanInfo = spans[telemetryId]
        val rootSpanInfo = rootSpans[telemetryId]
        if (spanStartEndEventsEnabled) {
            createSpanToCaptureEndSpanEvent(spanInfo)
        }
        spanInfo?.spanScope?.close()
        spanInfo?.span?.end()
        rootSpanInfo?.spanScope?.close()
        rootSpanInfo?.span?.end()
        spans.remove(telemetryId)
        rootSpans.remove(telemetryId)

        val baggageScope = baggages[telemetryId]
        baggageScope?.close()
        baggages.remove(telemetryId)
    }

    private fun createSpanToCaptureEndSpanEvent(spanInfo: SpanInfo?) {
        spanInfo?.parentSpanEventContext?.let {
            val startedSpanContext = it.createSpanContext()
            val startedSpanFromContext = Span.wrap(startedSpanContext)
            val startedSpanChild  = tracer.spanBuilder("${spanInfo.name}-end").setParent(Context.current().with(startedSpanFromContext)).startSpan()
            startedSpanChild.end()
        }
    }

    private fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
        val currentBaggage = Baggage.current()
        val baggageAttributes = mutableMapOf<String,String>()
        currentBaggage.forEach { t, u -> baggageAttributes[t] = u.value }

        val parentSpan = Span.current()
        val attributesMap = (attributes+baggageAttributes).toList().fold(Attributes.builder()) { builder, attribute -> builder.put(attribute.first, attribute.second) }.also {
            populateWithFlowAttributes(it, flowLogic)
        }.build()
        val span = tracer.spanBuilder(name).setAllAttributes(attributesMap).startSpan()
        val spanScope = span.makeCurrent()
        val startedEventContexts = createStartedEventSpan(name, attributesMap, parentSpan)
        spans[telemetryId] = SpanInfo(name, span, spanScope, startedEventContexts.first, startedEventContexts.second)
    }

    private fun populateWithFlowAttributes(attributesBuilder: AttributesBuilder, flowLogic: FlowLogic<*>?) {
        flowLogic?.let {
            attributesBuilder.put("flow.id", flowLogic.runId.uuid.toString())
            attributesBuilder.put("creation.time", flowLogic.stateMachine.creationTime)
            attributesBuilder.put("class.name", flowLogic.javaClass.name)
        }
    }

    private fun createStartedEventSpan(name: String, attributesMap: Attributes, parentSpan: Span): Pair<SerializableSpanContext?, SerializableSpanContext?> {
        if (spanStartEndEventsEnabled) {
            // Fix up null contexts - make not null
            val filteredSpans = spans.filter { it.value.span == parentSpan }.toList()
            var serializableSpanContext = SerializableSpanContext()
            var parentStartedSpanContext = SerializableSpanContext()
            if (filteredSpans.isNotEmpty()) {
                parentStartedSpanContext = filteredSpans[0].second.spanEventContext ?: SerializableSpanContext()
                val startedSpanContext = parentStartedSpanContext.createSpanContext()
                val startedSpanFromContext = Span.wrap(startedSpanContext)
                val startedSpanChild = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                        .setParent(Context.current().with(startedSpanFromContext)).startSpan()
                serializableSpanContext = SerializableSpanContext(startedSpanChild.spanContext)
                startedSpanChild.end()
            }
            return Pair(serializableSpanContext, parentStartedSpanContext)
        }
        else {
            return Pair(null, null)
        }
    }

    private fun endSpan(telemetryId: UUID){
        val spanInfo = spans[telemetryId]
        createSpanToCaptureEndSpanEvent(spanInfo)
        spanInfo?.spanScope?.close()
        spanInfo?.span?.end()
        spans.remove(telemetryId)
    }

    override fun getCurrentTelemetryData(): TelemetryDataItem {
        val currentSpan = Span.current()
        val spanContext = SerializableSpanContext(currentSpan.spanContext)
        val filteredSpans = spans.filter { it.value.span == currentSpan }.toList()
        val startedSpanContext = filteredSpans.getOrNull(0)?.second?.spanEventContext ?: SerializableSpanContext()
        return OpenTelemetryContext(spanContext, startedSpanContext, Baggage.current().asMap().mapValues { it.value.value })
    }

    override fun getCurrentTelemetryId(): UUID {
        val currentSpan = Span.current()
        val filteredSpans = spans.filter { it.value.span == currentSpan }.toList()
        if (filteredSpans.isEmpty()) {
            return UUID(0, 0)
        }
        return filteredSpans[0].first // return UUID associated with current span
    }

    override fun setCurrentTelemetryId(id: UUID) {
        val spanInfo = spans.get(id)
        spanInfo?.let {
            it.spanScope.close()  // close the old scope
            val childSpanScope = it.span.makeCurrent()
            val newSpanInfo = spanInfo.copy(spanScope = childSpanScope)
            spans[id] = newSpanInfo
        }
    }

    override fun getCurrentSpanId(): String {
        return Span.current().spanContext.spanId
    }

    override fun getCurrentTraceId(): String {
        return Span.current().spanContext.traceId
    }

    override fun getCurrentBaggage(): Map<String, String> {
        return Baggage.current().asMap().mapValues { it.value.value }
    }

    override fun getTelemetryHandles(): List<Any> {
        return tracerSetup.openTelemetry.let { listOf(it) }
    }

    private fun setStatus(telemetryId: UUID, telemetryStatusCode: TelemetryStatusCode, message: String) {
        val spanInfo = spans[telemetryId]
        spanInfo?.span?.setStatus(toOpenTelemetryStatus(telemetryStatusCode), message)
    }

    private fun toOpenTelemetryStatus(telemetryStatusCode: TelemetryStatusCode): StatusCode {
        return when(telemetryStatusCode) {
            TelemetryStatusCode.ERROR -> StatusCode.ERROR
            TelemetryStatusCode.OK -> StatusCode.OK
            TelemetryStatusCode.UNSET -> StatusCode.UNSET
        }
    }

    private fun recordException(telemetryId: UUID, throwable: Throwable) {
        val spanInfo = spans[telemetryId]
        spanInfo?.span?.recordException(throwable)
    }
}