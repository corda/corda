package core.protocols

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import core.messaging.MessageRecipients
import core.messaging.StateMachineManager
import core.node.ServiceHub
import core.serialization.SerializedBytes
import core.serialization.createKryo
import core.serialization.serialize
import core.utilities.UntrustworthyData
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A ProtocolStateMachine instance is a suspendable fiber that delegates all actual logic to a [ProtocolLogic] instance.
 * For any given flow there is only one PSM, even if that protocol invokes subprotocols.
 *
 * These classes are created by the [StateMachineManager] when a new protocol is started at the topmost level. If
 * a protocol invokes a sub-protocol, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
class ProtocolStateMachineImpl<R>(val logic: ProtocolLogic<R>, scheduler: FiberScheduler, val loggerName: String) : Fiber<R>("protocol", scheduler), ProtocolStateMachine<R> {

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient private var suspendAction: ((result: StateMachineManager.FiberRequest, serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>) -> Unit)? = null
    @Transient private var resumeWithObject: Any? = null
    @Transient lateinit override var serviceHub: ServiceHub

    @Transient private var _logger: Logger? = null
    override val logger: Logger get() {
        return _logger ?: run {
            val l = LoggerFactory.getLogger(loggerName)
            _logger = l
            return l
        }
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

    init {
        logic.psm = this
    }

    fun prepareForResumeWith(serviceHub: ServiceHub,
                             withObject: Any?,
                             suspendAction: (StateMachineManager.FiberRequest, SerializedBytes<ProtocolStateMachineImpl<*>>) -> Unit) {
        this.suspendAction = suspendAction
        this.resumeWithObject = withObject
        this.serviceHub = serviceHub
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
        suspend(with)
        val tmp = resumeWithObject ?: throw IllegalStateException("Expected to receive something")
        resumeWithObject = null
        return UntrustworthyData(tmp as T)
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun <T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long, sessionIDForReceive: Long,
                                          obj: Any, recvType: Class<T>): UntrustworthyData<T> {
        val result = StateMachineManager.FiberRequest.ExpectingResponse(topic, destination, sessionIDForSend, sessionIDForReceive, obj, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    override fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): UntrustworthyData<T> {
        val result = StateMachineManager.FiberRequest.ExpectingResponse(topic, null, -1, sessionIDForReceive, null, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    override fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any) {
        val result = StateMachineManager.FiberRequest.NotExpectingResponse(topic, destination, sessionID, obj)
        suspend(result)
    }

    @Suspendable
    private fun suspend(with: StateMachineManager.FiberRequest) {
        parkAndSerialize { fiber, serializer ->
            // We don't use the passed-in serializer here, because we need to use our own augmented Kryo.
            val deserializer = getFiberSerializer(false) as KryoSerializer
            val kryo = createKryo(deserializer.kryo)
            suspendAction!!(with, this.serialize(kryo))
        }
    }

}