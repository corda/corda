package net.corda.core.internal.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
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
import io.opentelemetry.context.propagation.TextMapGetter
import java.util.concurrent.ConcurrentLinkedDeque

@CordaSerializable
data class SpanEventContexts(val child: Context, val parent: Context)
@CordaSerializable
data class ContextCarrier(val context: MutableMap<String,String>)
@CordaSerializable
data class OpenTelemetryContext(val context: ContextCarrier, val spanEventChildContext: ContextCarrier, val spanEventParentContext: ContextCarrier, val baggage: Map<String,String>): TelemetryDataItem

data class SpanInfo(val name: String, val span: Span, val spanScope: Scope,
                    val spanEventContext: SpanEventContexts? = null,
                    val spanEventContextQueue: ConcurrentLinkedDeque<SpanEventContexts>? = null)

class TracerSetup(serviceName: String) {
    private var openTelemetryDriver: Any? = null
    val openTelemetry: OpenTelemetry by lazy {
        try {
            openTelemetryDriver = OpenTelemetryDriver(serviceName)
            (openTelemetryDriver as OpenTelemetryDriver).openTelemetry
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
class OpenTelemetryComponent(val serviceName: String, val spanStartEndEventsEnabled: Boolean, val copyBaggageToTags: Boolean) : TelemetryComponent {
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

    private fun extractContext(carrier: ContextCarrier): Context {
        val getter = object : TextMapGetter<ContextCarrier?> {
            override fun get(carrier: ContextCarrier?, key: String): String? {
                return if (carrier?.context?.containsKey(key) == true) {
                    val value = carrier.context[key]
                    value
                } else null
            }
            override fun keys(carrier: ContextCarrier?): MutableIterable<String> {
                return carrier?.context?.keys ?: mutableListOf()
            }
        }
        return carrier.let {
            tracerSetup.openTelemetry.propagators.textMapPropagator.extract(Context.current(), it, getter)
        }
    }

    @Suppress("LongParameterList")
    private fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?,
                                 telemetryDataItem: TelemetryDataItem?) {

        val openTelemetryContext = telemetryDataItem as? OpenTelemetryContext
        val extractedContext = openTelemetryContext?.let { extractContext(it.context) }
        val spanEventContexts = openTelemetryContext?.let { SpanEventContexts(extractContext(it.spanEventChildContext), extractContext(it.spanEventParentContext)) }
        val baggageAttributes = openTelemetryContext?.baggage?.let {
            val baggageBuilder = it.toList().fold(Baggage.current().toBuilder()) {builder, attribute -> builder.put(attribute.first, attribute.second)}
            baggages[telemetryId] = baggageBuilder.build().makeCurrent()
            it
        } ?: emptyMap()

        val allAttributes = if (copyBaggageToTags) {
            attributes + baggageAttributes
        }
        else {
            attributes
        }

        val attributesMap = allAttributes.toList()
                .fold(Attributes.builder()) { builder, attribute -> builder.put(attribute.first, attribute.second) }.also {
                    populateWithFlowAttributes(it, flowLogic)
                }.build()
        if (extractedContext != null && spanEventContexts != null) {
            startSpanForFlowWithRemoteParent(name, attributesMap, telemetryId, extractedContext, spanEventContexts)
        }
        else {
            startSpanForFlowWithNoParent(name, attributesMap, telemetryId)
        }
    }

    private fun startSpanForFlowWithRemoteParent(name: String, attributesMap: Attributes, telemetryId: UUID, parentContext: Context, spanEventContexts: SpanEventContexts) {
        val span = tracer.spanBuilder(name).setParent(parentContext).setAllAttributes(attributesMap).startSpan()
        val spanScope = span.makeCurrent()
        val contextAndQueue = startEndEventForFlowWithRemoteParent(name, attributesMap, spanEventContexts)
        spans[telemetryId] = SpanInfo(name, span, spanScope, contextAndQueue?.first, contextAndQueue?.second)
    }

    private fun startEndEventForFlowWithRemoteParent(name: String, attributesMap: Attributes, spanEventContexts: SpanEventContexts): Pair<SpanEventContexts, ConcurrentLinkedDeque<SpanEventContexts>>? {
        if (spanStartEndEventsEnabled) {
            val contexts = createSpanToCaptureStartedSpanEventWithRemoteParent(name, spanEventContexts, attributesMap)
            return Pair( contexts, ConcurrentLinkedDeque<SpanEventContexts>().also { it.add(contexts) })
        }
        return null
    }

    private fun startSpanForFlowWithNoParent(name: String, attributesMap: Attributes, telemetryId: UUID) {
        val rootSpan = tracer.spanBuilder(name).setAllAttributes(attributesMap).setAllAttributes(Attributes.of(AttributeKey.stringKey("root.flow"), "true")).startSpan()
        val rootSpanScope = rootSpan.makeCurrent()
        if (spanStartEndEventsEnabled) {
            val startedSpanContexts = createSpanToCaptureStartedSpanEvent(name, rootSpan, attributesMap)
            val span = tracer.spanBuilder("Child Spans").setParent(Context.current().with(rootSpan)).startSpan()
            val spanScope = span.makeCurrent()
            rootSpans[telemetryId] = SpanInfo(name, rootSpan, rootSpanScope)
            val spanEventContextStack = ConcurrentLinkedDeque<SpanEventContexts>().also { it.add(startedSpanContexts) }
            spans[telemetryId] = SpanInfo(name, span, spanScope, startedSpanContexts, spanEventContextStack)
        }
        else {
            spans[telemetryId] = SpanInfo(name, rootSpan, rootSpanScope)
        }
    }

    private fun createSpanToCaptureStartedSpanEvent(name: String, rootSpan: Span, attributesMap: Attributes): SpanEventContexts {
        val startedSpan = tracer.spanBuilder("Started Events").setAllAttributes(attributesMap).setAllAttributes(Attributes.of(AttributeKey.stringKey("root.startend.events"), "true")).setParent(Context.current().with(rootSpan)).startSpan()
        val parentContext = Context.current().with(startedSpan)
        startedSpan.end()
        val startedSpanChild = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                .setParent(parentContext).startSpan()
        val childContext = Context.current().with(startedSpanChild)
        startedSpanChild.end()
        return SpanEventContexts(childContext, parentContext)
    }

    private fun createSpanToCaptureStartedSpanEventWithRemoteParent(name: String, spanEventContexts: SpanEventContexts, attributesMap: Attributes ): SpanEventContexts {
        val startedSpanChild  = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                .setParent(spanEventContexts.child).startSpan()
        val grandChildContext = Context.current().with(startedSpanChild)
        startedSpanChild.end()
        return SpanEventContexts(grandChildContext, spanEventContexts.child)
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
        spanInfo?.spanEventContext?.parent?.let {
            val startedSpanChild  = tracer.spanBuilder("${spanInfo.name}-end").setParent(it).startSpan()
            startedSpanChild.end()
        }
        val spanEventContextStack = spanInfo?.spanEventContextQueue
        val filteredSpanEventContexts = spanEventContextStack?.filter { it == spanInfo.spanEventContext }
        filteredSpanEventContexts?.forEach { spanEventContextStack.remove(it) }
    }

    private fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
        val currentBaggage = Baggage.current()
        val baggageAttributes = mutableMapOf<String,String>()
        if (copyBaggageToTags) {
            currentBaggage.forEach { t, u -> baggageAttributes[t] = u.value }
        }
        val parentSpan = Span.current()
        val attributesMap = (attributes+baggageAttributes).toList().fold(Attributes.builder()) { builder, attribute -> builder.put(attribute.first, attribute.second) }.also {
            populateWithFlowAttributes(it, flowLogic)
        }.build()
        val span = tracer.spanBuilder(name).setAllAttributes(attributesMap).startSpan()
        val spanScope = span.makeCurrent()
        val spanEventContexts = createStartedEventSpan(name, attributesMap, parentSpan)
        spans[telemetryId] = SpanInfo(name, span, spanScope, spanEventContexts?.peekLast(), spanEventContexts)
    }

    private fun populateWithFlowAttributes(attributesBuilder: AttributesBuilder, flowLogic: FlowLogic<*>?) {
        flowLogic?.let {
            attributesBuilder.put("flow.id", flowLogic.runId.uuid.toString())
            attributesBuilder.put("creation.time", flowLogic.stateMachine.creationTime)
            attributesBuilder.put("class.name", flowLogic.javaClass.name)
        }
    }

    private fun createStartedEventSpan(name: String, attributesMap: Attributes, parentSpan: Span): ConcurrentLinkedDeque<SpanEventContexts>? {
        return if (spanStartEndEventsEnabled) {
            val filteredSpans = spans.filter { it.value.span == parentSpan }.toList()
            val (startEventParentContext, spanEventContextQueue) = getStartEventParentContext(filteredSpans, parentSpan)
            val startedSpanChild = tracer.spanBuilder("${name}-start").setAllAttributes(attributesMap)
                        .setParent(startEventParentContext).startSpan()
            val childContext = Context.current().with(startedSpanChild)
            startedSpanChild.end()
            spanEventContextQueue?.offer(SpanEventContexts(childContext, startEventParentContext))
            spanEventContextQueue
        }
        else {
            null
        }
    }

    private fun getStartEventParentContext(filteredSpans: List<Pair<UUID, SpanInfo>>, parentSpan: Span): Pair<Context, ConcurrentLinkedDeque<SpanEventContexts>?> {
        return if (filteredSpans.isNotEmpty()) {
            Pair(filteredSpans[0].second.spanEventContext?.child ?: Context.current(), filteredSpans[0].second.spanEventContextQueue)
        }
        else {
            // Copes with case where user has created their own span. So we just use the most
            // recent span we know about on the stack.
            val altFilteredSpans = spans.filter { it.value.span.spanContext.traceId == parentSpan.spanContext.traceId }.toList()
            val spanEventContexts = altFilteredSpans[0].second.spanEventContextQueue
            Pair(spanEventContexts?.peekLast()?.child ?: Context.current(), spanEventContexts)
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
        val currentContextCarrier = inject(tracerSetup, Context.current())
        val filteredSpans = spans.filter { it.value.span == currentSpan }.toList()

        val childContext = if (filteredSpans.isNotEmpty()) {
                filteredSpans.getOrNull(0)?.second?.spanEventContext?.child
            }
            else {
                val altFilteredSpans = spans.filter { it.value.span.spanContext.traceId == currentSpan.spanContext.traceId }.toList()
                if (altFilteredSpans.isNotEmpty()) {
                    altFilteredSpans[0].second.spanEventContextQueue?.peekLast()?.child
                }
                else {
                    null
                }
            }
        val childContextCarrier = inject(tracerSetup, childContext)

        val parentContext = if (filteredSpans.isNotEmpty()) {
            filteredSpans.getOrNull(0)?.second?.spanEventContext?.parent
        }
        else {
            val altFilteredSpans = spans.filter { it.value.span.spanContext.traceId == currentSpan.spanContext.traceId }.toList()
            if (altFilteredSpans.isNotEmpty()) {
                altFilteredSpans[0].second.spanEventContextQueue?.peekLast()?.parent
            }
            else {
                null
            }
        }
        val parentContextCarrier = inject(tracerSetup, parentContext)
        return OpenTelemetryContext(currentContextCarrier, childContextCarrier, parentContextCarrier, Baggage.current().asMap().mapValues { it.value.value })
    }

    private fun inject(tracerSetup: TracerSetup, context: Context?) : ContextCarrier {
        val contextCarrier = ContextCarrier(mutableMapOf())
        context?.let { tracerSetup.openTelemetry.propagators.textMapPropagator.inject(it, contextCarrier) { carrier, key, value ->  carrier?.context?.put(key, value) }}
        return contextCarrier
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
        val spanInfo = spans[id]
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
        return listOf(tracerSetup.openTelemetry)
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