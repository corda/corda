package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.utilities.UntrustworthyData
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
 * A ProtocolStateMachine instance is a suspendable fiber that delegates all actual logic to a [ProtocolLogic] instance.
 * For any given flow there is only one PSM, even if that protocol invokes subprotocols.
 *
 * These classes are created by the [StateMachineManager] when a new protocol is started at the topmost level. If
 * a protocol invokes a sub-protocol, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
interface ProtocolStateMachine<R> {
    @Suspendable
    fun <T : Any> sendAndReceive(otherParty: Party,
                                 payload: Any,
                                 receiveType: Class<T>,
                                 sessionProtocol: ProtocolLogic<*>): UntrustworthyData<T>

    @Suspendable
    fun <T : Any> receive(otherParty: Party, receiveType: Class<T>, sessionProtocol: ProtocolLogic<*>): UntrustworthyData<T>

    @Suspendable
    fun send(otherParty: Party, payload: Any, sessionProtocol: ProtocolLogic<*>)

    val serviceHub: ServiceHub
    val logger: Logger

    /** Unique ID for this machine run, valid across restarts */
    val id: StateMachineRunId
    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R>
}

class ProtocolSessionException(message: String) : Exception(message)