package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.UntrustworthyData
import org.slf4j.Logger
import java.security.Principal
import java.util.*

/**
 * FlowInitiator holds information on who started the flow. We have different ways of doing that: via RPC [FlowInitiator.RPC],
 * communication started by peer node [FlowInitiator.Peer], scheduled flows [FlowInitiator.Scheduled]
 * or via the Corda Shell [FlowInitiator.Shell].
 */
@CordaSerializable
sealed class FlowInitiator : Principal {
    /** Started using [net.corda.core.messaging.CordaRPCOps.startFlowDynamic]. */
    data class RPC(val username: String) : FlowInitiator() {
        override fun getName(): String = username
    }
    /** Started when we get new session initiation request. */
    data class Peer(val party: Party) : FlowInitiator() {
        override fun getName(): String = party.name.toString()
    }
    /** Started as scheduled activity. */
    data class Scheduled(val scheduledState: ScheduledStateRef) : FlowInitiator() {
        override fun getName(): String = "Scheduler"
    }
    // TODO When proper ssh access enabled, add username/use RPC?
    object Shell : FlowInitiator() {
        override fun getName(): String = "Shell User"
    }
}

/**
 * A unique identifier for a single state machine run, valid across node restarts. Note that a single run always
 * has at least one flow, but that flow may also invoke sub-flows: they all share the same run id.
 */
@CordaSerializable
data class StateMachineRunId(val uuid: UUID) {
    companion object {
        fun createRandom(): StateMachineRunId = StateMachineRunId(UUID.randomUUID())
    }

    override fun toString(): String = "[$uuid]"
}

/** This is an internal interface that is implemented by code in the node module. You should look at [FlowLogic]. */
interface FlowStateMachine<R> {
    @Suspendable
    fun <T : Any> sendAndReceive(receiveType: Class<T>,
                                 otherParty: Party,
                                 payload: Any,
                                 sessionFlow: FlowLogic<*>,
                                 retrySend: Boolean = false): UntrustworthyData<T>

    @Suspendable
    fun <T : Any> receive(receiveType: Class<T>, otherParty: Party, sessionFlow: FlowLogic<*>): UntrustworthyData<T>

    @Suspendable
    fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>)

    @Suspendable
    fun waitForLedgerCommit(hash: SecureHash, sessionFlow: FlowLogic<*>): SignedTransaction

    fun checkFlowPermission(permissionName: String, extraAuditData: Map<String,String>)

    fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String,String>)

    val serviceHub: ServiceHub
    val logger: Logger
    val id: StateMachineRunId
    val resultFuture: ListenableFuture<R>
    val flowInitiator: FlowInitiator
}
