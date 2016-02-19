/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.protocols

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.io.Output
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import core.ServiceHub
import core.messaging.MessageRecipients
import core.messaging.StateMachineManager
import core.serialization.createKryo
import core.utilities.UntrustworthyData
import org.slf4j.Logger
import java.io.ByteArrayOutputStream

/**
 * A ProtocolStateMachine instance is a suspendable fiber that delegates all actual logic to a [ProtocolLogic] instance.
 * For any given flow there is only one PSM, even if that protocol invokes subprotocols.
 *
 * These classes are created by the [StateMachineManager] when a new protocol is started at the topmost level. If
 * a protocol invokes a sub-protocol, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
class ProtocolStateMachine<R>(val logic: ProtocolLogic<R>) : Fiber<R>("protocol", StateMachineManager.SameThreadFiberScheduler) {
    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient private var suspendFunc: ((result: StateMachineManager.FiberRequest, serFiber: ByteArray) -> Unit)? = null
    @Transient private var resumeWithObject: Any? = null
    @Transient lateinit var serviceHub: ServiceHub
    @Transient lateinit var logger: Logger

    init {
        logic.psm = this
    }

    fun prepareForResumeWith(serviceHub: ServiceHub, withObject: Any?, logger: Logger,
                             suspendFunc: (StateMachineManager.FiberRequest, ByteArray) -> Unit) {
        this.suspendFunc = suspendFunc
        this.logger = logger
        this.resumeWithObject = withObject
        this.serviceHub = serviceHub
    }

    @Transient private var _resultFuture: SettableFuture<R>? = SettableFuture.create<R>()

    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R> get() {
        return _resultFuture ?: run {
            val f = SettableFuture.create<R>()
            _resultFuture = f
            return f
        }
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun run(): R {
        try {
            val result = logic.call()
            if (result != null)
                _resultFuture?.set(result)
            return result
        } catch (e: Throwable) {
            _resultFuture?.setException(e)
            throw e
        }
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    private fun <T : Any> suspendAndExpectReceive(with: StateMachineManager.FiberRequest): UntrustworthyData<T> {
        parkAndSerialize { fiber, serializer ->
            // We don't use the passed-in serializer here, because we need to use our own augmented Kryo.
            val deserializer = getFiberSerializer() as KryoSerializer
            val kryo = createKryo(deserializer.kryo)
            val stream = ByteArrayOutputStream()
            Output(stream).use {
                kryo.writeClassAndObject(it, this)
            }
            suspendFunc!!(with, stream.toByteArray())
        }
        val tmp = resumeWithObject ?: throw IllegalStateException("Expected to receive something")
        resumeWithObject = null
        return UntrustworthyData(tmp as T)
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    fun <T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long, sessionIDForReceive: Long,
                                 obj: Any, recvType: Class<T>): UntrustworthyData<T> {
        val result = StateMachineManager.FiberRequest.ExpectingResponse(topic, destination, sessionIDForSend, sessionIDForReceive, obj, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): UntrustworthyData<T> {
        val result = StateMachineManager.FiberRequest.ExpectingResponse(topic, null, -1, sessionIDForReceive, null, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any) {
        val result = StateMachineManager.FiberRequest.NotExpectingResponse(topic, destination, sessionID, obj)
        parkAndSerialize { fiber, writer -> suspendFunc!!(result, writer.write(fiber)) }
    }
}