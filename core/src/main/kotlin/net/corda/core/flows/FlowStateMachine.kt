package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.crypto.Party
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.UntrustworthyData
import org.slf4j.Logger
import java.util.*

data class StateMachineRunId private constructor(val uuid: UUID) {

    companion object {
        fun createRandom(): StateMachineRunId = StateMachineRunId(UUID.randomUUID())
        fun wrap(uuid: UUID): StateMachineRunId = StateMachineRunId(uuid)
    }

    override fun toString(): String = "[$uuid]"
}

/**
 * A FlowStateMachine instance is a suspendable fiber that delegates all actual logic to a [FlowLogic] instance.
 * For any given flow there is only one PSM, even if that flow invokes subflows.
 *
 * These classes are created by the [StateMachineManager] when a new flow is started at the topmost level. If
 * a flow invokes a sub-flow, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
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

    val serviceHub: ServiceHub
    val logger: Logger

    /** Unique ID for this machine run, valid across restarts */
    val id: StateMachineRunId
    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R>
}

class FlowException(message: String) : RuntimeException(message)
