package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.Destination
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.checkPayloadIs
import net.corda.core.internal.telemetry.SerializedTelemetry
import net.corda.core.internal.telemetry.telemetryServiceInternal
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.UntrustworthyData

class FlowSessionImpl(
        override val destination: Destination,
        private val wellKnownParty: Party,
        val sourceSessionId: SessionId,
        val serializedTelemetry: SerializedTelemetry?
) : FlowSession() {

    override val counterparty: Party get() = wellKnownParty

    override fun toString(): String = "FlowSessionImpl(destination=$destination, sourceSessionId=$sourceSessionId)"

    override fun equals(other: Any?): Boolean = other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    private val flowStateMachine: FlowStateMachine<*> get() = Fiber.currentFiber() as FlowStateMachine<*>
    private val telemetryMap = mapOf("destination" to destination.toString())

    @Suspendable
    override fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo {
        val request = FlowIORequest.GetFlowInfo(NonEmptySet.of(this))
        return flowStateMachine.suspend(request, maySkipCheckpoint).getValue(this)
    }

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo = getCounterpartyFlowInfo(maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> sendAndReceive(
            receiveType: Class<R>,
            payload: Any,
            maySkipCheckpoint: Boolean
    ): UntrustworthyData<R> {
        flowStateMachine.serviceHub.telemetryServiceInternal.span("${this::class.java.name}#sendAndReceive", telemetryMap, flowLogic = flowStateMachine.logic) {
            enforceNotPrimitive(receiveType)
            val request = FlowIORequest.SendAndReceive(
                    sessionToMessage = mapOf(this to payload.serialize(context = SerializationDefaults.P2P_CONTEXT)),
                    shouldRetrySend = false
            )
            val responseValues: Map<FlowSession, SerializedBytes<Any>> = flowStateMachine.suspend(request, maySkipCheckpoint)
            val responseForCurrentSession = responseValues.getValue(this)
            return responseForCurrentSession.checkPayloadIs(receiveType)
        }
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any) = sendAndReceive(receiveType, payload, maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
        flowStateMachine.serviceHub.telemetryServiceInternal.span("${this::class.java.name}#receive", telemetryMap, flowStateMachine.logic ) {
            enforceNotPrimitive(receiveType)
            val request = FlowIORequest.Receive(NonEmptySet.of(this))
            return flowStateMachine.suspend(request, maySkipCheckpoint).getValue(this).checkPayloadIs(receiveType)
        }
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>) = receive(receiveType, maySkipCheckpoint = false)

    @Suspendable
    override fun send(payload: Any, maySkipCheckpoint: Boolean) {
        flowStateMachine.serviceHub.telemetryServiceInternal.span("${this::class.java.name}#send", telemetryMap, flowStateMachine.logic) {
            val request = FlowIORequest.Send(
                    sessionToMessage = mapOf(this to payload.serialize(context = SerializationDefaults.P2P_CONTEXT))
            )
            return flowStateMachine.suspend(request, maySkipCheckpoint)
        }
    }

    @Suspendable
    override fun send(payload: Any) = send(payload, maySkipCheckpoint = false)

    @Suspendable
    override fun close() {
        val request = FlowIORequest.CloseSessions(NonEmptySet.of(this))
        return flowStateMachine.suspend(request, false)
    }

    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }
}
