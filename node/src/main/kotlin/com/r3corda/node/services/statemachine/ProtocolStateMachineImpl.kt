package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolStateMachine
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.node.services.api.ServiceHubInternal
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
class ProtocolStateMachineImpl<R>(val logic: ProtocolLogic<R>,
                                  scheduler: FiberScheduler,
                                  private val loggerName: String)
: Fiber<R>("protocol", scheduler), ProtocolStateMachine<R> {

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient lateinit override var serviceHub: ServiceHubInternal
    @Transient internal lateinit var suspendAction: (StateMachineManager.FiberRequest) -> Unit
    @Transient internal lateinit var actionOnEnd: () -> Unit
    @Transient internal var receivedPayload: Any? = null

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

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun run(): R {
        val result = try {
            logic.call()
        } catch (t: Throwable) {
            actionOnEnd()
            _resultFuture?.setException(t)
            throw t
        }

        // This is to prevent actionOnEnd being called twice if it throws an exception
        actionOnEnd()
        _resultFuture?.set(result)
        return result
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    private fun <T : Any> suspendAndExpectReceive(with: StateMachineManager.FiberRequest): UntrustworthyData<T> {
        suspend(with)
        check(receivedPayload != null) { "Expected to receive something" }
        val untrustworthy = UntrustworthyData(receivedPayload as T)
        receivedPayload = null
        return untrustworthy
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
            try {
                suspendAction!!(with)
            } catch (t: Throwable) {
                logger.warn("Captured exception which was swallowed by Quasar", t)
                // TODO to throw or not to throw, that is the question
                throw t
            }
        }
    }

}
