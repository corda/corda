package net.corda.core.node.services

import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@CordaSerializable
interface TelemetryDataItem

@CordaSerializable
data class SerializedTelemetry(val serializedTelemetryData: Map<String, OpaqueBytes>)

enum class StatusCode {
    /** The default status.  */
    UNSET,

    /**
     * The operation has been validated by an Application developers or Operator to have completed
     * successfully.
     */
    OK,

    /** The operation contains an error.  */
    ERROR
}

@CordaSerializable
data class TelemetryId(private val telemetryService: TelemetryService) {
    val id: UUID = UUID.randomUUID()

    fun setStatus(statusCode: StatusCode, message: String) {
        telemetryService.setStatus(this, statusCode, message)
    }

    fun recordException(throwable: Throwable) {
        telemetryService.recordException( this, throwable)
    }

    fun close() {
        telemetryService.endSpan(this)
    }
}

@CordaSerializable
data class ComponentTelemetryIds(val componentTelemetryIds: Map<String, UUID>)



interface TelemetryEvent

class StartSpanForFlowEvent(val name: String,
                       val attributes: Map<String, String>,
                       val telemetryId: UUID, val flowLogic: FlowLogic<*>?, val telemetryDataItem: TelemetryDataItem?): TelemetryEvent
class EndSpanForFlowEvent(val telemetryId: UUID): TelemetryEvent
class StartSpanEvent(val name: String, val attributes: Map<String, String>, val telemetryId: UUID, val flowLogic: FlowLogic<*>?): TelemetryEvent
class EndSpanEvent(val telemetryId: UUID): TelemetryEvent
class SetStatusEvent(val telemetryId: UUID, val statusCode: StatusCode, val message: String): TelemetryEvent
class RecordExceptionEvent(val telemetryId: UUID, val throwable: Throwable): TelemetryEvent

interface TelemetryComponent {
    fun isEnabled(): Boolean
    fun name(): String
    fun onTelemetryEvent(event: TelemetryEvent)
    fun getCurrentTelemetryData(): TelemetryDataItem
    fun getCurrentTelemetryId(): UUID
    fun setCurrentTelemetryId(id: UUID)
}

class TelemetryService : SingletonSerializeAsToken() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TelemetryService::class.java)
    }
    fun setStatus(telemetryId: TelemetryId, statusCode: StatusCode, message: String) {
        telemetryComponents.forEach {
            it.onTelemetryEvent(SetStatusEvent(telemetryId.id, statusCode, message))
        }
    }

    fun recordException(telemetryId: TelemetryId, throwable: Throwable) {
        telemetryComponents.forEach {
            it.onTelemetryEvent(RecordExceptionEvent(telemetryId.id, throwable))
        }
    }

    fun deserialize(data: OpaqueBytes): TelemetryDataItem {
        return SerializationFactory.defaultFactory.deserialize(data, TelemetryDataItem::class.java, SerializationFactory.defaultFactory.defaultContext)
    }

    private val telemetryComponents: MutableList<TelemetryComponent> = mutableListOf()

    fun addTelemetryComponent(telemetryComponent: TelemetryComponent) {
        telemetryComponents.add(telemetryComponent)
    }

    fun startSpanForFlow(name: String, attributes: Map<String, String>, flowLogic: FlowLogic<*>? = null, remoteSerializedTelemetry: SerializedTelemetry? = null): TelemetryId {
        val telemetryId = TelemetryId(this)
        telemetryComponents.forEach {
            val bytes = remoteSerializedTelemetry?.serializedTelemetryData?.get(it.name())
            val telemetryDataItem = bytes?.let { deserialize(bytes) }
            it.onTelemetryEvent(StartSpanForFlowEvent(name, attributes, telemetryId.id, flowLogic, telemetryDataItem))
        }
        return telemetryId
    }

    fun endSpanForFlow(telemetryId: TelemetryId) {
        telemetryComponents.forEach {
            it.onTelemetryEvent(EndSpanForFlowEvent(telemetryId.id))
        }
    }

    fun startSpan(name: String, attributes: Map<String, String> = emptyMap(), flowLogic: FlowLogic<*>? = null): TelemetryId {
        val telemetryId = TelemetryId(this)
        telemetryComponents.forEach {
            it.onTelemetryEvent(StartSpanEvent(name, attributes, telemetryId.id, flowLogic))
        }
        return telemetryId
    }

    fun endSpan(telemetryId: TelemetryId) {
        telemetryComponents.forEach {
            it.onTelemetryEvent(EndSpanEvent(telemetryId.id))
        }
    }

    fun getCurrentTelemetryData(): SerializedTelemetry {
        val serializedTelemetryData = mutableMapOf<String, OpaqueBytes>()
        telemetryComponents.forEach {
            val currentTelemetryData = it.getCurrentTelemetryData()
            serializedTelemetryData[it.name()] = currentTelemetryData.serialize()
        }
        return SerializedTelemetry(serializedTelemetryData)
    }

    fun getCurrentTelemetryIds(): ComponentTelemetryIds? {
        if (telemetryComponents.isEmpty()) {
            return null
        }
        val telemetryIds = mutableMapOf<String, UUID>()
        telemetryComponents.forEach {
            telemetryIds[it.name()] = it.getCurrentTelemetryId()
        }
        return ComponentTelemetryIds(telemetryIds)
    }

    fun setCurrentTelemetryId(telemetryIds: ComponentTelemetryIds) {
        telemetryComponents.forEach {
            it.setCurrentTelemetryId(telemetryIds.componentTelemetryIds[it.name()]!!)
        }
    }
}
