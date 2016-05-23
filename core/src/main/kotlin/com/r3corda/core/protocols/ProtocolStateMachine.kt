package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.utilities.UntrustworthyData
import org.slf4j.Logger


/**
 * The interface of [ProtocolStateMachineImpl] exposing methods and properties required by ProtocolLogic for compilation
 */
interface ProtocolStateMachine<R> {
    @Suspendable
    fun <T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long, sessionIDForReceive: Long,
                                 obj: Any, recvType: Class<T>): UntrustworthyData<T>

    @Suspendable
    fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): UntrustworthyData<T>

    @Suspendable
    fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any)

    val serviceHub: ServiceHub
    val logger: Logger
}
