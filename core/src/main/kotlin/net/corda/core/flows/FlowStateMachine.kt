package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.UntrustworthyData
import org.slf4j.Logger
import java.util.*

/**
 * CommunicationInitiator holds information on who started the flow. We have different ways of doing that: via RPC [CommunicationInitiator.Rpc],
 * communication started by peer node [CommunicationInitiator.Peer], scheduled flows [CommunicationInitiator.Scheduled]
 * or manual [CommunicationInitiator.Manual]. The last case is for all flows started in tests, shell etc. It was added
 * added because we can start flow directly using [StateMachineManager.add] or [ServiceHubInternal.startFlow].
 */
@CordaSerializable
sealed class CommunicationInitiator {
    override fun toString(): String = javaClass.simpleName

    abstract class WithName(open val name: String): CommunicationInitiator() {
        override fun toString() = javaClass.simpleName + ": $name"
    }

    class Manual(override val name: String): CommunicationInitiator.WithName(name) // TODO Think of name here. What with shell access?
    /** Started using [CordaRPCOps.startFlowDynamic]. Name is RPC username. */
    class Rpc(override val name: String): CommunicationInitiator.WithName(name)
    /** Started when we get new session initiation request. Name is peer legal name. */
    class Peer(override val name: String): CommunicationInitiator.WithName(name)
    class Scheduled: CommunicationInitiator()
    /** When constructing [FlowLogic] communication initiator defaults to [NotStarted]*/
    class NotStarted: CommunicationInitiator()
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
                                 sessionFlow: FlowLogic<*>): UntrustworthyData<T>

    @Suspendable
    fun <T : Any> receive(receiveType: Class<T>, otherParty: Party, sessionFlow: FlowLogic<*>): UntrustworthyData<T>

    @Suspendable
    fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>)

    @Suspendable
    fun waitForLedgerCommit(hash: SecureHash, sessionFlow: FlowLogic<*>): SignedTransaction

    fun createHandle(hasProgress: Boolean): FlowHandle<R>

    val serviceHub: ServiceHub
    val logger: Logger
    val id: StateMachineRunId
    val resultFuture: ListenableFuture<R>
}
