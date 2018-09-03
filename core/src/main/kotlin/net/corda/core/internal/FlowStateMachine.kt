package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import org.slf4j.Logger

/** This is an internal interface that is implemented by code in the node module. You should look at [FlowLogic]. */
@DeleteForDJVM
@DoNotImplement
interface FlowStateMachine<FLOWRETURN> {
    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>, maySkipCheckpoint: Boolean): SUSPENDRETURN

    @Suspendable
    fun initiateFlow(party: Party): FlowSession

    fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>)

    fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>)

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(subFlow: FlowLogic<SUBFLOWRETURN>): SUBFLOWRETURN

    @Suspendable
    fun flowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot?

    @Suspendable
    fun persistFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>)

    val logic: FlowLogic<FLOWRETURN>
    val serviceHub: ServiceHub
    val logger: Logger
    val id: StateMachineRunId
    val resultFuture: CordaFuture<FLOWRETURN>
    val context: InvocationContext
    val ourIdentity: Party
    val ourSenderUUID: String?
    val creationTime: Long
}
