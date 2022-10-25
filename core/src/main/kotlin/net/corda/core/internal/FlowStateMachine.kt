package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.internal.telemetry.SerializedTelemetry
import net.corda.core.serialization.SerializedBytes
import org.slf4j.Logger

@DeleteForDJVM
@DoNotImplement
interface FlowStateMachineHandle<FLOWRETURN> {
    val logic: FlowLogic<FLOWRETURN>?
    val id: StateMachineRunId
    val resultFuture: CordaFuture<FLOWRETURN>
    val clientId: String?
}

/** This is an internal interface that is implemented by code in the node module. You should look at [FlowLogic]. */
@DeleteForDJVM
@DoNotImplement
interface FlowStateMachine<FLOWRETURN> : FlowStateMachineHandle<FLOWRETURN> {
    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>, maySkipCheckpoint: Boolean): SUSPENDRETURN

    fun serialize(payloads: Map<FlowSession, Any>): Map<FlowSession, SerializedBytes<Any>>

    @Suspendable
    fun initiateFlow(destination: Destination, wellKnownParty: Party, serializedTelemetry: SerializedTelemetry?): FlowSession

    fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>)

    fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>)

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(currentFlow: FlowLogic<*>, subFlow: FlowLogic<SUBFLOWRETURN>): SUBFLOWRETURN

    @Suspendable
    fun flowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot?

    @Suspendable
    fun persistFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>)

    fun updateTimedFlowTimeout(timeoutSeconds: Long)

    val serviceHub: ServiceHub
    val logger: Logger
    val context: InvocationContext
    val ourIdentity: Party
    val ourSenderUUID: String?
    val creationTime: Long
    val isKilled: Boolean
}