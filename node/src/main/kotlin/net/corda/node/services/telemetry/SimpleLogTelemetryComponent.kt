package net.corda.core.node.services

import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*

@CordaSerializable
data class SimpleLogContext(val traceId: UUID, val baggage: Map<String, String>): TelemetryDataItem

// Simple telemetry class that creates a single UUID and uses this for the trace id. When the flow starts we use the trace is passed in. After this
// though we must use the trace id propagated to us (if remote), or the trace id associated with thread local.
@Suppress("TooManyFunctions")
class SimpleLogTelemetryComponent : TelemetryComponent {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleLogTelemetryComponent::class.java)
    }

    private val traces: InheritableThreadLocal<UUID> = InheritableThreadLocal()
    private val logContexts = mutableMapOf<UUID, SimpleLogContext>()

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
        val traceId = (telemetryDataItem as? SimpleLogContext)?.traceId ?: telemetryId
        val flowId = flowLogic?.runId
        val clientId = (telemetryDataItem as? SimpleLogContext)?.baggage?.get("client.id") ?: flowLogic?.stateMachine?.clientId
        val externalId = (telemetryDataItem as? SimpleLogContext)?.baggage?.get("external.id") ?: externalIdParam
        traces.set(traceId)
        val baggageAttributes = (telemetryDataItem as? SimpleLogContext)?.baggage ?: populateBaggageWithFlowAttributes(flowLogic, externalId)

        logContexts[traceId] = SimpleLogContext(traceId, baggageAttributes)
        // check below re. the name - do we have some convention here?
        clientId?.let { MDC.put("client.id", it) }
        externalId?.let { MDC.put("external.id", it)}
        MDC.put("trace.id", traceId.toString())
        log.debug {"startSpanForFlow: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: ${attributes+baggageAttributes}"}
    }

    private fun populateBaggageWithFlowAttributes(flowLogic: FlowLogic<*>?, externalId: String?): Map<String, String> {
        val baggageAttributes = mutableMapOf<String, String>()
        flowLogic?.stateMachine?.clientId?.let { baggageAttributes["client.id"] = it }
        externalId?.let { baggageAttributes["external.id"] = it }
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