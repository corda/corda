package net.corda.core.node.services

import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*

@CordaSerializable
data class SimpleLogContext(val traceId: UUID): TelemetryDataItem

// Simple telemetry class that creates a single UUID and uses this for the trace id. When the flow starts we use the trace is passed in. After this
// though we must use the trace id propagated to us (if remote), or the trace id associated with thread local.
class SimpleLogTelemetryComponent : TelemetryComponent {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleLogTelemetryComponent::class.java)
        private val traces: InheritableThreadLocal<UUID> = InheritableThreadLocal()
    }

    override fun isEnabled(): Boolean {
        return true
    }

    override fun name(): String = "SimpleLogTelemetry"

    override fun onTelemetryEvent(ev: TelemetryEvent) {
        when (ev) {
            is StartSpanForFlowEvent -> startSpanForFlow(ev.name, ev.attributes, ev.telemetryId, ev.flowLogic, ev.telemetryDataItem)
            is EndSpanForFlowEvent -> endSpanForFlow(ev.telemetryId)
            is StartSpanEvent -> startSpan(ev.name, ev.attributes, ev.telemetryId, ev.flowLogic)
            is EndSpanEvent -> endSpan(ev.telemetryId)
            is SetStatusEvent -> setStatus(ev.telemetryId, ev.statusCode, ev.message)
            is RecordExceptionEvent -> recordException(ev.telemetryId, ev.throwable)
        }
    }

    private fun startSpanForFlow(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?, telemetryDataItem: TelemetryDataItem?) {
        val traceId = (telemetryDataItem as? SimpleLogContext)?.traceId ?: telemetryId
        val flowId = flowLogic?.runId
        val clientId = flowLogic?.stateMachine?.clientId
        traces.set(traceId)
        // check below re. the name - do we have some convention here?
        MDC.put("corda.traceid", traceId.toString())
        log.info("startSpanForFlow: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: $attributes")
    }

    // Check when you start a top level flow the startSpanForFlow appears just once, and so the endSpanForFlow also appears just once
    // So its valid to do the MDC clear here. For remotes nodes as well
    private fun endSpanForFlow(telemetryId: UUID) {
        log.info("endSpanForFlow: traceId: ${traces.get()}")
        MDC.clear()
    }

    private fun startSpan(name: String, attributes: Map<String, String>, telemetryId: UUID, flowLogic: FlowLogic<*>?) {
        val flowId = flowLogic?.runId
        val clientId = flowLogic?.stateMachine?.clientId
        val traceId = traces.get()
        log.info("startSpan: name: $name, traceId: $traceId, flowId: $flowId, clientId: $clientId, attributes: $attributes")
    }

    private fun endSpan(telemetryId: UUID) {
        log.info("endSpan: traceId: ${traces.get()}")
    }

    override fun getCurrentTelemetryData(): SimpleLogContext {
        traces.get()?.let {
            return SimpleLogContext(it)
        }
        log.info("getCurrentTelemetryData called on SimpleLogTelemetry but UUID not set, return all zeros")
        return SimpleLogContext(UUID(0,0))
    }

    override fun getCurrentTelemetryId(): UUID {
        return traces.get() ?: UUID(0,0)
    }

    override fun setCurrentTelemetryId(id: UUID) {
        traces.set(id)
    }

    private fun setStatus(telemetryId: UUID, statusCode: StatusCode, message: String) {
        when(statusCode) {
            StatusCode.ERROR -> log.error("setStatus: traceId: ${traces.get()}, statusCode: ${statusCode}, message: message")
            StatusCode.OK, StatusCode.UNSET -> log.info("setStatus: traceId: ${traces.get()}, statusCode: ${statusCode}, message: message")
        }
    }

    private fun recordException(telemetryId: UUID, throwable: Throwable) {
        log.error("recordException: traceId: ${traces.get()}, throwable: ${throwable}}")
    }
}