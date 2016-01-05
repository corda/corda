/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import core.SecureHash
import core.ServiceHub
import core.serialization.THREAD_LOCAL_KRYO
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.sha256
import core.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

/**
 * A StateMachineManager is responsible for coordination and persistence of multiple [ProtocolStateMachine] objects.
 * Each such object represents an instantiation of a (two-party) protocol that has reached a particular point.
 *
 * An implementation of this class will persist state machines to long term storage so they can survive process restarts
 * and, if run with a single-threaded executor, will ensure no two state machines run concurrently with each other
 * (bad for performance, good for programmer mental health!).
 *
 * A "state machine" is a class with a single call method. The call method and any others it invokes are rewritten by
 * a bytecode rewriting engine called JavaFlow, to ensure the code can be suspended and resumed at any point.
 *
 * TODO: The framework should propagate exceptions and handle error handling automatically.
 * TODO: This needs extension to the >2 party case.
 * TODO: Consider the issue of continuation identity more deeply: is it a safe assumption that a serialised
 *       continuation is always unique?
 * TODO: Think about how to bring the system to a clean stop so it can be upgraded without any serialised stacks on disk
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHub, val runInThread: Executor) {
    // This map is backed by a database and will be used to store serialised state machines to disk, so we can resurrect
    // them across node restarts.
    private val checkpointsMap = serviceHub.storageService.getMap<SecureHash, ByteArray>("state machines")
    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val _stateMachines = Collections.synchronizedList(ArrayList<ProtocolStateMachine<*,*>>())

    /** Returns a snapshot of the currently registered state machines. */
    val stateMachines: List<ProtocolStateMachine<*,*>> get() {
        synchronized(_stateMachines) {
            return ArrayList(_stateMachines)
        }
    }

    // Used to work around a small limitation in Quasar.
    private val QUASAR_UNBLOCKER = run {
        val field = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
        field.isAccessible = true
        field.get(null)
    }

    // This class will be serialised, so everything it points to transitively must also be serialisable (with Kryo).
    private class Checkpoint(
        val serialisedFiber: ByteArray,
        val otherSide: MessageRecipients,
        val loggerName: String,
        val awaitingTopic: String,
        val awaitingObjectOfType: String   // java class name
    )

    init {
        restoreCheckpoints()
    }

    /** Reads the database map and resurrects any serialised state machines. */
    private fun restoreCheckpoints() {
        for (bytes in checkpointsMap.values) {
            val checkpoint = bytes.deserialize<Checkpoint>()
            val checkpointKey = SecureHash.sha256(bytes)

            // Grab the Kryo engine configured by Quasar for its own stuff, and then do our own configuration on top
            // so we can deserialised the nested stream that holds the fiber.
            val psm = deserializeFiber(checkpoint.serialisedFiber)
            _stateMachines.add(psm)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            val topic = checkpoint.awaitingTopic

            // And now re-wire the deserialised continuation back up to the network service.
            serviceHub.networkService.runOnNextMessage(topic, runInThread) { netMsg ->
                val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), awaitingObjectOfType)
                logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
                iterateStateMachine(psm, serviceHub.networkService, logger, obj, checkpoint.otherSide, checkpointKey) {
                    Fiber.unparkDeserialized(it, SameThreadFiberScheduler)
                }
            }
        }
    }

    private fun deserializeFiber(bits: ByteArray): ProtocolStateMachine<*, *> {
        val deserializer = Fiber.getFiberSerializer() as KryoSerializer
        val kryo = createKryo(deserializer.kryo)
        val psm = kryo.readClassAndObject(Input(bits)) as ProtocolStateMachine<*, *>
        return psm
    }

    /**
     * Kicks off a brand new state machine of the given class. It will send messages to the network node identified by
     * the [otherSide] parameter, log with the named logger, and the [initialArgs] object will be passed to the call
     * method of the [ProtocolStateMachine] object that is created. The state machine will be persisted when it suspends
     * and will be removed once it completes.
     */
    fun <T : ProtocolStateMachine<I, *>, I> add(otherSide: MessageRecipients, initialArgs: I, loggerName: String,
                                                klass: Class<out T>): T {
        val logger = LoggerFactory.getLogger(loggerName)
        val fiber = klass.newInstance()
        iterateStateMachine(fiber, serviceHub.networkService, logger, initialArgs, otherSide, null) {
            it.start()
        }
        return fiber
    }

    private fun persistCheckpoint(prevCheckpointKey: SecureHash?, new: ByteArray): SecureHash {
        // It's OK for this to be unsynchronised, as the prev/new byte arrays are specific to a continuation instance,
        // and the underlying map provided by the database layer is expected to be thread safe.
        if (prevCheckpointKey != null)
            checkpointsMap.remove(prevCheckpointKey)
        val key = SecureHash.sha256(new)
        checkpointsMap[key] = new
        return key
    }

    private fun iterateStateMachine(psm: ProtocolStateMachine<*, *>, net: MessagingService, logger: Logger,
                                    obj: Any?, otherSide: MessageRecipients, prevCheckpointKey: SecureHash?,
                                    resumeFunc: (ProtocolStateMachine<*, *>) -> Unit) {
        val onSuspend = fun(request: FiberRequest, serFiber: ByteArray) {
            // We have a request to do something: send, receive, or send-and-receive.
            if (request is FiberRequest.ExpectingResponse<*>) {
                // Prepare a listener on the network that runs in the background thread when we received a message.
                checkpointAndSetupMessageHandler(logger, net, psm, otherSide, request.responseType,
                        "${request.topic}.${request.sessionIDForReceive}", prevCheckpointKey, serFiber)
            }
            // If an object to send was provided (not null), send it now.
            request.obj?.let {
                val topic = "${request.topic}.${request.sessionIDForSend}"
                logger.trace { "-> $topic : message of type ${it.javaClass.name}" }
                net.send(net.createMessage(topic, it.serialize().bits), otherSide)
            }
            if (request is FiberRequest.NotExpectingResponse) {
                // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
                iterateStateMachine(psm, net, logger, null, otherSide, prevCheckpointKey) {
                    Fiber.unpark(it, QUASAR_UNBLOCKER)
                }
            }
        }

        psm.prepareForResumeWith(serviceHub, obj, logger, onSuspend)

        try {
            // Now either start or carry on with the protocol from where it left off (or at the start).
            resumeFunc(psm)

            // We're back! Check if the fiber is finished and if so, clean up.
            if (psm.isTerminated) {
                _stateMachines.remove(psm)
                checkpointsMap.remove(prevCheckpointKey)
            }
        } catch (t: Throwable) {
            logger.error("Caught error whilst invoking protocol state machine", t)
            throw t
        }
    }

    private fun checkpointAndSetupMessageHandler(logger: Logger, net: MessagingService, psm: ProtocolStateMachine<*,*>,
                                                 otherSide: MessageRecipients, responseType: Class<*>,
                                                 topic: String, prevCheckpointKey: SecureHash?, serialisedFiber: ByteArray) {
        val checkpoint = Checkpoint(serialisedFiber, otherSide, logger.name, topic, responseType.name)
        val curPersistedBytes = checkpoint.serialize().bits
        persistCheckpoint(prevCheckpointKey, curPersistedBytes)
        val newCheckpointKey = curPersistedBytes.sha256()
        net.runOnNextMessage(topic, runInThread) { netMsg ->
            val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), responseType)
            logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
            iterateStateMachine(psm, net, logger, obj, otherSide, newCheckpointKey) {
                Fiber.unpark(it, QUASAR_UNBLOCKER)
            }
        }
    }
}

object SameThreadFiberScheduler : FiberExecutorScheduler("Same thread scheduler", MoreExecutors.directExecutor())

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
abstract class ProtocolStateMachine<C, R> : Fiber<R>("protocol", SameThreadFiberScheduler) {
    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient private var suspendFunc: ((result: FiberRequest, serFiber: ByteArray) -> Unit)? = null
    @Transient private var resumeWithObject: Any? = null
    @Transient protected lateinit var serviceHub: ServiceHub
    @Transient protected lateinit var logger: Logger
    @Transient private var _resultFuture: SettableFuture<R> = SettableFuture.create<R>()
    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R> get() = _resultFuture

    fun prepareForResumeWith(serviceHub: ServiceHub, withObject: Any?, logger: Logger,
                             suspendFunc: (FiberRequest, ByteArray) -> Unit) {
        this.suspendFunc = suspendFunc
        this.logger = logger
        this.resumeWithObject = withObject
        this.serviceHub = serviceHub
    }

    @Suspendable
    abstract fun call(args: C): R

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun run(): R {
        val result = call(resumeWithObject!! as C)
        if (result != null)
            _resultFuture.set(result)
        return result
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    private fun <T : Any> suspendAndExpectReceive(with: FiberRequest): T {
        Fiber.parkAndSerialize { fiber, serializer ->
            // We don't use the passed-in serializer here, because we need to use our own augmented Kryo.
            val deserializer = Fiber.getFiberSerializer() as KryoSerializer
            val kryo = createKryo(deserializer.kryo)
            val stream = ByteArrayOutputStream()
            Output(stream).use {
                kryo.writeClassAndObject(it, this)
            }
            suspendFunc!!(with, stream.toByteArray())
        }
        val tmp = resumeWithObject ?: throw IllegalStateException("Expected to receive something")
        resumeWithObject = null
        return tmp as T
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    protected fun <T : Any> sendAndReceive(topic: String, sessionIDForSend: Long, sessionIDForReceive: Long,
                                           obj: Any, recvType: Class<T>): T {
        val result = FiberRequest.ExpectingResponse(topic, sessionIDForSend, sessionIDForReceive, obj, recvType)
        return suspendAndExpectReceive<T>(result)
    }

    @Suspendable
    protected fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): T {
        val result = FiberRequest.ExpectingResponse(topic, -1, sessionIDForReceive, null, recvType)
        return suspendAndExpectReceive<T>(result)
    }

    @Suspendable
    protected fun send(topic: String, sessionID: Long, obj: Any) {
        val result = FiberRequest.NotExpectingResponse(topic, sessionID, obj)
        Fiber.parkAndSerialize { fiber, writer -> suspendFunc!!(result, writer.write(fiber)) }
    }

    // Convenience functions for Kotlin users.
    inline protected fun <reified R : Any> sendAndReceive(topic: String, sessionIDForSend: Long,
                                                          sessionIDForReceive: Long, obj: Any): R {
        return sendAndReceive(topic, sessionIDForSend, sessionIDForReceive, obj, R::class.java)
    }
    inline protected fun <reified R : Any> receive(topic: String, sessionIDForReceive: Long): R {
        return receive(topic, sessionIDForReceive, R::class.java)
    }
}

open class FiberRequest(val topic: String, val sessionIDForSend: Long, val sessionIDForReceive: Long, val obj: Any?) {
    class ExpectingResponse<R : Any>(
            topic: String,
            sessionIDForSend: Long,
            sessionIDForReceive: Long,
            obj: Any?,
            val responseType: Class<R>
    ) : FiberRequest(topic, sessionIDForSend, sessionIDForReceive, obj)

    class NotExpectingResponse(topic: String, sessionIDForSend: Long, obj: Any?) : FiberRequest(topic, sessionIDForSend, -1, obj)
}