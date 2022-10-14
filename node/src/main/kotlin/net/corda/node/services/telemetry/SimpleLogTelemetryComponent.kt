package net.corda.node.services.telemetry

import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.EndSpanEvent
import net.corda.core.node.services.EndSpanForFlowEvent
import net.corda.core.node.services.RecordExceptionEvent
import net.corda.core.node.services.SetStatusEvent
import net.corda.core.node.services.StartSpanEvent
import net.corda.core.node.services.StartSpanForFlowEvent
import net.corda.core.node.services.StatusCode
import net.corda.core.node.services.TelemetryComponent
import net.corda.core.node.services.TelemetryDataItem
import net.corda.core.node.services.TelemetryEvent
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@CordaSerializable
data class SimpleLogContext(val traceId: UUID, val baggage: Map<String, String>): TelemetryDataItem

const val CLIENT_ID = "client.id"
const val EXTERNAL_ID = "external.id"
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
            is StartSpanForFlowEvent -> startSpanForFlow(event.name, event.attributes, event.telemetryId, event.flowLogic, event.externalId, event.telemetryDataItem)
            is EndSpanForFlowEvent -> endSpanForFlow(event.telemetryId)
            is StartSpanEvent -> startSpan(event.name, event.attributes, event.telemetryId, event.flowLogic)
            is EndSpanEvent -> endSpan(event.telemetryId)
            is SetStatusEvent -> setStatus(event.telemetryId, event.statusCode, event.message)
            is RecordExceptionEvent -> recordException(event.telemetryId, event.throwable)
        }
    }

    @Suppress("LongParameterList")
    private fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?, externalIdParam: String?, telemetryDataItem: TelemetryDataItem?) {
        val simpleLogTelemetryDataItem = telemetryDataItem?.let {(telemetryDataItem as? SimpleLogContext) ?:
                                throw IllegalStateException("Type of telemetryDataItem no a SimpleLogContext, actual class is ${telemetryDataItem::class.java.name}")}
        val traceId = simpleLogTelemetryDataItem?.traceId ?: telemetryId
        val flowId = flowLogic?.runId
        val clientId = simpleLogTelemetryDataItem?.baggage?.get(CLIENT_ID) ?: flowLogic?.stateMachine?.clientId
        val externalId = simpleLogTelemetryDataItem?.baggage?.get(EXTERNAL_ID) ?: externalIdParam
        traces.set(traceId)
        val baggageAttributes = simpleLogTelemetryDataItem?.baggage ?: populateBaggageWithFlowAttributes(flowLogic, externalId)

        logContexts[traceId] = SimpleLogContext(traceId, baggageAttributes)
        clientId?.let { MDC.put(CLIENT_ID, it) }
        externalId?.let { MDC.put(EXTERNAL_ID, it)}
        MDC.put(TRACE_ID, traceId.toString())
        log.debug {"startSpanForFlow: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: ${attributes+baggageAttributes}"}
    }

    private fun populateBaggageWithFlowAttributes(flowLogic: FlowLogic<*>?, externalId: String?): Map<String, String> {
        val baggageAttributes = mutableMapOf<String, String>()
        flowLogic?.stateMachine?.clientId?.let { baggageAttributes[CLIENT_ID] = it }
        externalId?.let { baggageAttributes[EXTERNAL_ID] = it }
        return baggageAttributes
    }

    // Check when you start a top level flow the startSpanForFlow appears just once, and so the endSpanForFlow also appears just once
    // So its valid to do the MDC clear here. For remotes nodes as well
    private fun endSpanForFlow(telemetryId: UUID) {
        log.debug {"endSpanForFlow: traceId: ${traces.get()}"}
        logContexts.remove(telemetryId)
        MDC.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
        val flowId = flowLogic?.runId
        val clientId = flowLogic?.stateMachine?.clientId
        val traceId = traces.get()
        log.debug {"startSpan: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: $attributes"}
    }

    @Suppress("UNUSED_PARAMETER")
    private fun endSpan(telemetryId: UUID) {
        log.debug {"endSpan: traceId: ${traces.get()}"}
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

    @Suppress("UNUSED_PARAMETER")
    private fun setStatus(telemetryId: UUID, statusCode: StatusCode, message: String) {
        when(statusCode) {
            StatusCode.ERROR -> log.error("setStatus: traceId: ${traces.get()}, statusCode: ${statusCode}, message: message")
            StatusCode.OK, StatusCode.UNSET -> log.debug {"setStatus: traceId: ${traces.get()}, statusCode: ${statusCode}, message: message" }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun recordException(telemetryId: UUID, throwable: Throwable) {
        log.error("recordException: traceId: ${traces.get()}, throwable: ${throwable}}")
    }
}