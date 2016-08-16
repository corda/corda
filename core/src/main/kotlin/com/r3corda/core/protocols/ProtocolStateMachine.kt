package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.utilities.UntrustworthyData
import org.slf4j.Logger


/**
 * The interface of [ProtocolStateMachineImpl] exposing methods and properties required by ProtocolLogic for compilation.
 */
interface ProtocolStateMachine<R> {
    @Suspendable
    fun <T : Any> sendAndReceive(topic: String, destination: Party, sessionIDForSend: Long, sessionIDForReceive: Long,
                                 payload: Any, recvType: Class<T>): UntrustworthyData<T>

    @Suspendable
    fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): UntrustworthyData<T>

    @Suspendable
    fun send(topic: String, destination: Party, sessionID: Long, payload: Any)

    val serviceHub: ServiceHub
    val logger: Logger

    /** Unique ID for this machine, valid only while it is in memory. */
    val machineId: Long
    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R>
}
