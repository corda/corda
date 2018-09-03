package net.corda.node.services.api

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

/**
 * Minimum event specific data for any audit event to be logged. It is expected that the underlying audit service
 * will enrich this to include details of the node, so that in clustered configurations the source node can be identified.
 */
sealed class AuditEvent {
    /**
     * The UTC time point at which the audit event happened.
     */
    abstract val timestamp: Instant
    /**
     * The invocation context at the time the event was generated.
     */
    abstract val context: InvocationContext
    /**
     * A human readable description of audit event including any permission check results.
     */
    abstract val description: String
    /**
     * Further tagged details that should be recorded along with the common data of the audit event.
     * Examples of this might be trade identifiers, system error codes, or source IP addresses, which could be useful
     * when searching the historic audit data for trails of evidence.
     */
    abstract val contextData: Map<String, String>
}

/**
 * Sealed data class to mark system related events as a distinct category.
 */
data class SystemAuditEvent(override val timestamp: Instant,
                            override val context: InvocationContext,
                            override val description: String,
                            override val contextData: Map<String, String>) : AuditEvent()

/**
 * Interface to mandate flow identification properties
 */
interface FlowAuditInfo {
    /**
     * The concrete type of FlowLogic being referenced.
     * TODO This should be replaced with the fully versioned name/signature of the flow.
     */
    val flowType: Class<out FlowLogic<*>>
    /**
     * The stable identifier of the flow as stored with Checkpoints.
     */
    val flowId: StateMachineRunId
}

/**
 * Sealed data class to record custom application specified flow event.
 */
data class FlowAppAuditEvent(
        override val timestamp: Instant,
        override val context: InvocationContext,
        override val description: String,
        override val contextData: Map<String, String>,
        override val flowType: Class<out FlowLogic<*>>,
        override val flowId: StateMachineRunId,
        val auditEventType: String) : AuditEvent(), FlowAuditInfo

/**
 * Sealed data class to record the initiation of a new flow.
 * The flow parameters should be captured to the context data.
 */
data class FlowStartEvent(
        override val timestamp: Instant,
        override val context: InvocationContext,
        override val description: String,
        override val contextData: Map<String, String>,
        override val flowType: Class<out FlowLogic<*>>,
        override val flowId: StateMachineRunId) : AuditEvent(), FlowAuditInfo

/**
 * Sealed data class to record ProgressTracker Step object whenever a change is signalled.
 * The API for ProgressTracker has been extended so that the Step can contain some extra context data,
 * which is copied into the contextData Map.
 */
data class FlowProgressAuditEvent(
        override val timestamp: Instant,
        override val context: InvocationContext,
        override val description: String,
        override val flowType: Class<out FlowLogic<*>>,
        override val flowId: StateMachineRunId,
        val flowProgress: ProgressTracker.Step) : AuditEvent(), FlowAuditInfo {
    override val contextData: Map<String, String> get() = flowProgress.extraAuditData
}

/**
 * Sealed data class to record any FlowExceptions, or other unexpected terminations of a Flow.
 */
data class FlowErrorAuditEvent(override val timestamp: Instant,
                               override val context: InvocationContext,
                               override val description: String,
                               override val contextData: Map<String, String>,
                               override val flowType: Class<out FlowLogic<*>>,
                               override val flowId: StateMachineRunId,
                               val error: Throwable) : AuditEvent(), FlowAuditInfo

/**
 * Sealed data class to record  checks on per flow permissions and the verdict of these checks
 * If the permission is denied i.e. permissionGranted is false, then it is expected that the flow will be terminated immediately
 * after recording the FlowPermissionAuditEvent. This may cause an extra FlowErrorAuditEvent to be recorded too.
 */
data class FlowPermissionAuditEvent(override val timestamp: Instant,
                                    override val context: InvocationContext,
                                    override val description: String,
                                    override val contextData: Map<String, String>,
                                    override val flowType: Class<out FlowLogic<*>>,
                                    override val flowId: StateMachineRunId,
                                    val permissionRequested: String,
                                    val permissionGranted: Boolean) : AuditEvent(), FlowAuditInfo

/**
 * Minimal interface for recording audit information within the system. The AuditService is assumed to be available only
 * to trusted internal components via ServiceHubInternal.
 */
interface AuditService {
    fun recordAuditEvent(event: AuditEvent)
}

/**
 * Empty do nothing AuditService as placeholder.
 * TODO Write a full implementation that expands all the audit events to the database.
 */
class DummyAuditService : AuditService, SingletonSerializeAsToken() {
    override fun recordAuditEvent(event: AuditEvent) {
        //TODO Implement transformation of the audit events to formal audit data
    }
}

