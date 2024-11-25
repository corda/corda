package net.corda.core.internal.telemetry

import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@CordaSerializable
data class SimpleLogContext(val traceId: UUID, val baggage: Map<String, String>): TelemetryDataItem

const val CLIENT_ID = "client.id"
const val TRACE_ID = "trace.id"

// Simple telemetry class that creates a single UUID and uses this for the trace id. When the flow starts we use the trace is passed in. After this
// though we must use the trace id propagated to us (if remote), or the trace id associated with thread local.
@Suppress("TooManyFunctions")
class SimpleLogTelemetryComponent : TelemetryComponent {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleLogTelemetryComponent::class.java)
    }

    private val traces: InheritableThreadLocal<UUID> = InheritableThreadLocal()
    private val logContexts = ConcurrentHashMap<UUID, SimpleLogContext>()

    override fun isEnabled(): Boolean {
        return true
    }

    override fun name(): String = "SimpleLogTelemetry"

    override fun onTelemetryEvent(event: TelemetryEvent) {
        when (event) {
            is StartSpanForFlowEvent -> startSpanForFlow(event.name, event.attributes, event.telemetryId, event.flowLogic, event.telemetryDataItem)
            is EndSpanForFlowEvent -> endSpanForFlow(event.telemetryId)
            is StartSpanEvent -> startSpan(event.name, event.attributes, event.telemetryId, event.flowLogic)
            is EndSpanEvent -> endSpan(event.telemetryId)
            is SetStatusEvent -> setStatus(event.telemetryId, event.telemetryStatusCode, event.message)
            is RecordExceptionEvent -> recordException(event.telemetryId, event.throwable)
        }
    }

    @Suppress("LongParameterList")
    private fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?, telemetryDataItem: TelemetryDataItem?) {
        val simpleLogTelemetryDataItem = telemetryDataItem?.let {(telemetryDataItem as? SimpleLogContext) ?:
                                throw IllegalStateException("Type of telemetryDataItem no a SimpleLogContext, actual class is ${telemetryDataItem::class.java.name}")}
        val traceId = simpleLogTelemetryDataItem?.traceId ?: telemetryId
        val flowId = flowLogic?.runId
        val clientId = simpleLogTelemetryDataItem?.baggage?.get(CLIENT_ID) ?: flowLogic?.stateMachine?.clientId
        traces.set(traceId)
        val baggageAttributes = simpleLogTelemetryDataItem?.baggage ?: emptyMap()

        logContexts[traceId] = SimpleLogContext(traceId, baggageAttributes)
        clientId?.let { MDC.put(CLIENT_ID, it) }
        MDC.put(TRACE_ID, traceId.toString())
        log.info("startSpanForFlow: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: ${attributes+baggageAttributes}")
    }

    // Check when you start a top level flow the startSpanForFlow appears just once, and so the endSpanForFlow also appears just once
    // So its valid to do the MDC clear here. For remotes nodes as well
    private fun endSpanForFlow(telemetryId: UUID) {
        log.info("endSpanForFlow: traceId: ${traces.get()}")
        logContexts.remove(telemetryId)
        MDC.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
        val flowId = flowLogic?.runId
        val clientId = flowLogic?.stateMachine?.clientId
        val traceId = traces.get()
        log.info("startSpan: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: $attributes")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun endSpan(telemetryId: UUID) {
        log.info("endSpan: traceId: ${traces.get()}")
    }

    override fun getCurrentTelemetryData(): SimpleLogContext {
        traces.get()?.let {
            logContexts[it]?.let { simpleLogContext ->
                return simpleLogContext
            }
        }
        return SimpleLogContext(UUID(0, 0), emptyMap())
    }

    override fun getCurrentTelemetryId(): UUID {
        return traces.get() ?: UUID(0,0)
    }

    override fun setCurrentTelemetryId(id: UUID) {
        traces.set(id)
    }

    override fun getCurrentSpanId(): String {
        return traces.get()?.toString() ?: ""
    }

    override fun getCurrentTraceId(): String {
        return traces.get()?.toString() ?: ""
    }

    override fun getCurrentBaggage(): Map<String, String> {
        val uuid = traces.get()
        return logContexts[uuid]?.baggage ?: emptyMap()
    }

    override fun getTelemetryHandles(): List<Any> {
        return emptyList()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setStatus(telemetryId: UUID, telemetryStatusCode: TelemetryStatusCode, message: String) {
        when(telemetryStatusCode) {
            TelemetryStatusCode.ERROR -> log.error("setStatus: traceId: ${traces.get()}, statusCode: ${telemetryStatusCode}, message: message")
            TelemetryStatusCode.OK, TelemetryStatusCode.UNSET -> log.info("setStatus: traceId: ${traces.get()}, statusCode: ${telemetryStatusCode}, message: message")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun recordException(telemetryId: UUID, throwable: Throwable) {
        log.error("recordException: traceId: ${traces.get()}, throwable: ${throwable}}")
    }
}