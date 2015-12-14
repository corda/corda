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
import core.SecureHash
import core.ServiceHub
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
import java.util.concurrent.Executor

/**
 * A StateMachineManager is responsible for coordination and persistence of multiple [ProtocolStateMachine] objects.
 * Each such object represents an instantiation of a (two-party) protocol that has reached a particular point.
 *
 * An implementation of this class will persist state machines to long term storage so they can survive process restarts
 * and, if run with a single-threaded executor, will ensure no two state machines run concurrently with each other
 * (bad for performance, good for programmer mental health!).
 *
 * TODO: The framework should propagate exceptions and handle error handling automatically.
 */
class StateMachineManager(val serviceHub: ServiceHub, val runInThread: Executor) {
    private val checkpointsMap = serviceHub.storageService.getMap<SecureHash, ByteArray>("state machines")
    private val _stateMachines: MutableList<ProtocolStateMachine<*,*>> = ArrayList()

    /** Returns a snapshot of the currently registered state machines. */
    val stateMachines: List<ProtocolStateMachine<*,*>> get() = ArrayList(_stateMachines)

    // This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
    private class Checkpoint(
            val continuation: Continuation,
            val otherSide: MessageRecipients,
            val loggerName: String,
            val awaitingTopic: String,
            val awaitingObjectOfType: String   // java class name
    )

    init {
        restoreCheckpoints()
    }

    private fun restoreCheckpoints() {
        for (bytes in checkpointsMap.values) {
            val kryo = createKryo()

            // Set up Kryo to use the JavaFlow classloader when deserialising, so the magical continuation bytecode
            // rewriting is performed correctly.
            var psm: ProtocolStateMachine<*, *>? = null
            kryo.instantiatorStrategy = object : InstantiatorStrategy {
                val forwardingTo = kryo.instantiatorStrategy

                override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
                    if (ProtocolStateMachine::class.java.isAssignableFrom(type)) {
                        // The messing around with types we do here confuses the compiler/IDE a bit and it warns us.
                        @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
                        return ObjectInstantiator<T> {
                            val p = loadContinuationClass(type as Class<out ProtocolStateMachine<*, *>>).first
                            p.serviceHub = serviceHub
                            psm = p
                            psm as T
                        }
                    } else {
                        return forwardingTo.newInstantiatorOf(type)
                    }
                }
            }
            val checkpoint = bytes.deserialize<Checkpoint>(kryo)

            val continuation = checkpoint.continuation
            _stateMachines.add(psm!!)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            // The act of calling this method re-persists the bytes into the in-memory hashmap so re-saving the
            // StateMachineManager to disk will work even if some state machines didn't wake up in the intervening time.
            setupNextMessageHandler(logger, serviceHub.networkService, continuation, checkpoint.otherSide,
                    awaitingObjectOfType, checkpoint.awaitingTopic, bytes)
        }
    }

    fun <T : ProtocolStateMachine<I, *>, I> add(otherSide: MessageRecipients, initialArgs: I, loggerName: String,
                                                continuationClass: Class<out T>): T {
        val logger = LoggerFactory.getLogger(loggerName)
        val (sm, continuation) = loadContinuationClass(continuationClass)
        sm.serviceHub = serviceHub
        _stateMachines.add(sm)
        runInThread.execute {
            // The current state of the continuation is held in the closure attached to the messaging system whenever
            // the continuation suspends and tells us it expects a response.
            iterateStateMachine(continuation, serviceHub.networkService, otherSide, initialArgs, logger, null)
        }
        @Suppress("UNCHECKED_CAST")
        return sm as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadContinuationClass(continuationClass: Class<out ProtocolStateMachine<*, *>>): Pair<ProtocolStateMachine<*, *>, Continuation> {
        val url = continuationClass.protectionDomain.codeSource.location
        val cl = ContinuationClassLoader(arrayOf(url), this.javaClass.classLoader)
        val obj = cl.forceLoadClass(continuationClass.name).newInstance() as ProtocolStateMachine<*, *>
        return Pair(obj, Continuation.startSuspendedWith(obj))
    }

    private fun persistCheckpoint(prev: ByteArray?, new: ByteArray) {
        if (prev != null)
            checkpointsMap.remove(SecureHash.sha256(prev))
        checkpointsMap[SecureHash.sha256(new)] = new
    }

    private fun iterateStateMachine(c: Continuation, net: MessagingService, otherSide: MessageRecipients,
                                    continuationInput: Any?, logger: Logger,
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
            setupNextMessageHandler(logger, net, nextState, otherSide, req.responseType, topic, prevPersistedBytes)
        }
        // If an object to send was provided (not null), send it now.
        req.obj?.let {
            val topic = "${req.topic}.${req.sessionIDForSend}"
            logger.trace { "-> $topic : message of type ${it.javaClass.name}" }
            net.send(net.createMessage(topic, it.serialize()), otherSide)
        }
        if (req is ContinuationResult.NotExpectingResponse) {
            // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
            return iterateStateMachine(nextState, net, otherSide, null, logger, prevPersistedBytes)
        } else {
            return nextState
        }
    }

    private fun setupNextMessageHandler(logger: Logger, net: MessagingService, nextState: Continuation,
                                        otherSide: MessageRecipients, responseType: Class<*>,
                                        topic: String, prevPersistedBytes: ByteArray?) {
        val checkpoint = Checkpoint(nextState, otherSide, logger.name, topic, responseType.name)
        val curPersistedBytes = checkpoint.serialize()
        persistCheckpoint(prevPersistedBytes, curPersistedBytes)
        net.runOnNextMessage(topic, runInThread) { netMsg ->
            val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), responseType)
            logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
            iterateStateMachine(nextState, net, otherSide, obj, logger, curPersistedBytes)
        }
    }
}

val CONTINUATION_LOGGER = ThreadLocal<Logger>()

/**
 * The base class that should be used by any object that wishes to act as a protocol state machine. Sub-classes should
 * override the [call] method and return whatever the final result of the protocol is. Inside the call method,
 * the rules of normal object oriented programming are a little different:
 *
 * - You can call send/receive/sendAndReceive in order to suspend the state machine and request network interaction.
 *   This does not block a thread and when a state machine is suspended like this, it will be serialised and written
 *   to stable storage. That means all objects on the stack and referenced from fields must be serialisable as well
 *   (with Kryo, so they don't have to implement the Java Serializable interface). The state machine may be resumed
 *   at some arbitrary later point.
 * - Because of this, if you need access to data that might change over time, you should request it just-in-time
 *   via the [serviceHub] property which is provided. Don't try and keep data you got from a service across calls to
 *   send/receive/sendAndReceive because the world might change in arbitrary ways out from underneath you, for instance,
 *   if the node is restarted or reconfigured!
 * - Don't pass initial data in using a constructor. This object will be instantiated using reflection so you cannot
 *   define your own constructor. Instead define a separate class that holds your initial arguments, and take it as
 *   the argument to [call].
 */
@Suppress("UNCHECKED_CAST")
abstract class ProtocolStateMachine<T, R> : Runnable {
    protected fun logger(): Logger = CONTINUATION_LOGGER.get()

    // These fields shouldn't be serialised.
    @Transient private var _resultFuture: SettableFuture<R> = SettableFuture.create<R>()
    val resultFuture: ListenableFuture<R> get() = _resultFuture
    @Transient lateinit var serviceHub: ServiceHub

    abstract fun call(args: T): R

    override fun run() {
        val r = call(Continuation.getContext() as T)
        if (r != null)
            _resultFuture.set(r)
    }
}

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
inline fun <S : Any> ProtocolStateMachine<*, *>.send(topic: String, sessionID: Long, obj: S) =
        Continuation.suspend(ContinuationResult.NotExpectingResponse(topic, sessionID, obj))

@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any> ProtocolStateMachine<*, *>.sendAndReceive(
        topic: String, sessionIDForSend: Long, sessionIDForReceive: Long, obj: Any): R {
    return Continuation.suspend(ContinuationResult.ExpectingResponse(topic, sessionIDForSend, sessionIDForReceive,
            obj, R::class.java)) as R
}


@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any> ProtocolStateMachine<*, *>.receive(
        topic: String, sessionIDForReceive: Long): R {
    return Continuation.suspend(ContinuationResult.ExpectingResponse(topic, -1, sessionIDForReceive, null, R::class.java)) as R
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