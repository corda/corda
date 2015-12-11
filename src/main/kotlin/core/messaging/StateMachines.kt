/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import com.esotericsoftware.kryo.io.Input
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import core.serialization.THREAD_LOCAL_KRYO
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.trace
import org.apache.commons.javaflow.Continuation
import org.apache.commons.javaflow.ContinuationClassLoader
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * A StateMachineManager is responsible for coordination and persistence of multiple [ProtocolStateMachine] objects.
 *
 * An implementation of this class will persist state machines to long term storage so they can survive process restarts
 * and, if run with a single-threaded executor, will ensure no two state machines run concurrently with each other
 * (bad for performance, good for programmer mental health!).
 */
class StateMachineManager(val net: MessagingSystem, val runInThread: Executor) {
    // This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
    private class Checkpoint(
            val continuation: Continuation,
            val otherSide: MessageRecipients,
            val loggerName: String,
            val awaitingTopic: String,
            val awaitingObjectOfType: String   // java class name
    )

    constructor(net: MessagingSystem, runInThread: Executor, restoreCheckpoints: List<ByteArray>, resumeStateMachine: (ProtocolStateMachine<*,*>) -> Any) : this(net, runInThread) {
        for (bytes in restoreCheckpoints) {
            val kryo = createKryo()

            // Set up Kryo to use the JavaFlow classloader when deserialising, so the magical continuation bytecode
            // rewriting is performed correctly.
            var psm: ProtocolStateMachine<*,*>? = null
            kryo.instantiatorStrategy = object : InstantiatorStrategy {
                val forwardingTo = kryo.instantiatorStrategy

                override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
                    if (ProtocolStateMachine::class.java.isAssignableFrom(type)) {
                        // The messing around with types we do here confuses the compiler/IDE a bit and it warns us.
                        @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
                        return ObjectInstantiator<T> {
                            psm = loadContinuationClass(type as Class<out ProtocolStateMachine<*, Any>>).first
                            psm as T
                        }
                    } else {
                        return forwardingTo.newInstantiatorOf(type)
                    }
                }
            }
            val checkpoint = bytes.deserialize<Checkpoint>(kryo)

            val continuation = checkpoint.continuation
            val transientContext = resumeStateMachine(psm!!)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            // The act of calling this method re-persists the bytes into the in-memory hashmap so re-saving the
            // StateMachineManager to disk will work even if some state machines didn't wake up in the intervening time.
            setupNextMessageHandler(logger, net, continuation, checkpoint.otherSide, awaitingObjectOfType,
                    checkpoint.awaitingTopic, transientContext, bytes)
        }
    }

    fun <R> add(otherSide: MessageRecipients, transientContext: Any, loggerName: String, continuationClass: Class<out ProtocolStateMachine<*, R>>): ListenableFuture<out R> {
        val logger = LoggerFactory.getLogger(loggerName)
        val (sm, continuation) = loadContinuationClass<R>(continuationClass)
        runInThread.execute {
            // The current state of the continuation is held in the closure attached to the messaging system whenever
            // the continuation suspends and tells us it expects a response.
            iterateStateMachine(continuation, net, otherSide, transientContext, transientContext, logger, null)
        }
        return sm.resultFuture
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> loadContinuationClass(continuationClass: Class<out ProtocolStateMachine<*, R>>): Pair<ProtocolStateMachine<*,R>, Continuation> {
        val url = continuationClass.protectionDomain.codeSource.location
        val cl = ContinuationClassLoader(arrayOf(url), this.javaClass.classLoader)
        val obj = cl.forceLoadClass(continuationClass.name).newInstance() as ProtocolStateMachine<*, R>
        return Pair(obj, Continuation.startSuspendedWith(obj))
    }

    private val checkpoints: LinkedList<ByteArray> = LinkedList()
    private fun persistCheckpoint(prev: ByteArray?, new: ByteArray) {
        synchronized(checkpoints) {
            if (prev == null) {
                for (i in checkpoints.size - 1 downTo 0) {
                    val b = checkpoints[i]
                    if (Arrays.equals(b, prev)) {
                        checkpoints[i] = new
                        return
                    }
                }
            }
            checkpoints.add(new)
        }
    }

    fun saveToBytes(): LinkedList<ByteArray> = synchronized(checkpoints) { LinkedList(checkpoints) }

    private fun iterateStateMachine(c: Continuation, net: MessagingSystem, otherSide: MessageRecipients,
                                    transientContext: Any, continuationInput: Any?, logger: Logger,
                                    prevPersistedBytes: ByteArray?): Continuation {
        // This will resume execution of the run() function inside the continuation at the place it left off.
        val oldLogger = CONTINUATION_LOGGER.get()
        val nextState: Continuation? = try {
            CONTINUATION_LOGGER.set(logger)
            Continuation.continueWith(c, continuationInput)
        } catch (t: Throwable) {
            logger.error("Caught error whilst invoking protocol state machine", t)
            throw t
        } finally {
            CONTINUATION_LOGGER.set(oldLogger)
        }

        // If continuation returns null, it's finished and the result future has been set.
        if (nextState == null)
            return c

        val req = nextState.value() as? ContinuationResult ?: return c

        // Else, it wants us to do something: send, receive, or send-and-receive.
        if (req is ContinuationResult.ExpectingResponse<*>) {
            // Prepare a listener on the network that runs in the background thread when we received a message.
            val topic = "${req.topic}.${req.sessionIDForReceive}"
            setupNextMessageHandler(logger, net, nextState, otherSide, req.responseType, topic, transientContext, prevPersistedBytes)
        }
        // If an object to send was provided (not null), send it now.
        req.obj?.let {
            val topic = "${req.topic}.${req.sessionIDForSend}"
            logger.trace { "-> $topic : message of type ${it.javaClass.name}" }
            net.send(net.createMessage(topic, it.serialize()), otherSide)
        }
        if (req is ContinuationResult.NotExpectingResponse) {
            // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
            return iterateStateMachine(nextState, net, otherSide, transientContext, transientContext, logger, prevPersistedBytes)
        } else {
            return nextState
        }
    }

    private fun setupNextMessageHandler(logger: Logger, net: MessagingSystem, nextState: Continuation,
                                        otherSide: MessageRecipients, responseType: Class<*>,
                                        topic: String, transientContext: Any, prevPersistedBytes: ByteArray?) {
        val checkpoint = Checkpoint(nextState, otherSide, logger.name, topic, responseType.name)
        persistCheckpoint(prevPersistedBytes, checkpoint.serialize())
        net.runOnNextMessage(topic, runInThread) { netMsg ->
            val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), responseType)
            logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
            iterateStateMachine(nextState, net, otherSide, transientContext, Pair(transientContext, obj), logger, prevPersistedBytes)
        }
    }
}

val CONTINUATION_LOGGER = ThreadLocal<Logger>()

/**
 * A convenience mixin interface that can be implemented by an object that will act as a continuation.
 *
 * A ProtocolStateMachine must implement the run method from [Runnable], and the rest of what this interface
 * provides are pre-defined utility methods to ease implementation of such machines.
 */
@Suppress("UNCHECKED_CAST")
abstract class ProtocolStateMachine<CONTEXT_TYPE : Any, R> : Callable<R>, Runnable {
    protected fun context(): CONTEXT_TYPE = Continuation.getContext() as CONTEXT_TYPE
    protected fun logger(): Logger = CONTINUATION_LOGGER.get()

    // These fields shouldn't be serialised.
    @Transient private var _resultFuture: SettableFuture<R> = SettableFuture.create<R>()

    val resultFuture: ListenableFuture<R> get() = _resultFuture

    override fun run() {
        val r = call()
        if (r != null)
            _resultFuture.set(r)
    }
}

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
inline fun <S : Any, CONTEXT_TYPE : Any> ProtocolStateMachine<CONTEXT_TYPE, *>.send(topic: String, sessionID: Long, obj: S) =
        Continuation.suspend(ContinuationResult.NotExpectingResponse(topic, sessionID, obj)) as CONTEXT_TYPE

@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any, CONTEXT_TYPE : Any> ProtocolStateMachine<CONTEXT_TYPE, *>.sendAndReceive(
        topic: String, sessionIDForSend: Long, sessionIDForReceive: Long, obj: Any): Pair<CONTEXT_TYPE, R> {
    return Continuation.suspend(ContinuationResult.ExpectingResponse(topic, sessionIDForSend, sessionIDForReceive,
            obj, R::class.java)) as Pair<CONTEXT_TYPE, R>
}


@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any, CONTEXT_TYPE : Any> ProtocolStateMachine<CONTEXT_TYPE, *>.receive(
        topic: String, sessionIDForReceive: Long): Pair<CONTEXT_TYPE, R> {
    return Continuation.suspend(ContinuationResult.ExpectingResponse(topic, -1, sessionIDForReceive, null,
            R::class.java)) as Pair<CONTEXT_TYPE, R>
}

open class ContinuationResult(val topic: String, val sessionIDForSend: Long, val sessionIDForReceive: Long, val obj: Any?) {
    class ExpectingResponse<R : Any>(
            topic: String,
            sessionIDForSend: Long,
            sessionIDForReceive: Long,
            obj: Any?,
            val responseType: Class<R>
    ) : ContinuationResult(topic, sessionIDForSend, sessionIDForReceive, obj)

    class NotExpectingResponse(topic: String, sessionIDForSend: Long, obj: Any?) : ContinuationResult(topic, sessionIDForSend, -1, obj)
}