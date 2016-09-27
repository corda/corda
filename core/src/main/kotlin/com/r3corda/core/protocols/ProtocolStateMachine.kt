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
    }

    override fun toString(): String = "${javaClass.simpleName}($uuid)"
}

/**
 * The interface of [ProtocolStateMachineImpl] exposing methods and properties required by ProtocolLogic for compilation.
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