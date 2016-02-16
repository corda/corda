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
import com.google.common.base.Throwables
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import core.ServiceHub
import core.crypto.SecureHash
import core.crypto.sha256
import core.serialization.THREAD_LOCAL_KRYO
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
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
 * a bytecode rewriting engine called Quasar, to ensure the code can be suspended and resumed at any point.
 *
 * TODO: Session IDs should be set up and propagated automatically, on demand.
 * TODO: Consider the issue of continuation identity more deeply: is it a safe assumption that a serialised
 *       continuation is always unique?
 * TODO: Think about how to bring the system to a clean stop so it can be upgraded without any serialised stacks on disk
 * TODO: Timeouts
 * TODO: Surfacing of exceptions via an API and/or management UI
 * TODO: Ability to control checkpointing explicitly, for cases where you know replaying a message can't hurt
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHub, val runInThread: Executor) {
    // This map is backed by a database and will be used to store serialised state machines to disk, so we can resurrect
    // them across node restarts.
    private val checkpointsMap = serviceHub.storageService.getMap<SecureHash, ByteArray>("state machines")
    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val _stateMachines = Collections.synchronizedList(ArrayList<ProtocolLogic<*>>())

    // This is a workaround for something Gradle does to us during unit tests. It replaces stderr with its own
    // class that inserts itself into a ThreadLocal. That then gets caught in fiber serialisation, which we don't
    // want because it can't get recreated properly. It turns out there's no good workaround for this! All the obvious
    // approaches fail. Pending resolution of https://github.com/puniverse/quasar/issues/153 we just disable
    // checkpointing when unit tests are run inside Gradle. The right fix is probably to make Quasar's
    // bit-too-clever-for-its-own-good ThreadLocal serialisation trick. It already wasted far more time than it can
    // ever recover.
    val checkpointing: Boolean get() = !System.err.javaClass.name.contains("LinePerThreadBufferingOutputStream")

    /** Returns a list of all state machines executing the given protocol logic at the top level (subprotocols do not count) */
    fun <T> findStateMachines(klass: Class<out ProtocolLogic<T>>): List<Pair<ProtocolLogic<T>, ListenableFuture<T>>> {
        synchronized(_stateMachines) {
            @Suppress("UNCHECKED_CAST")
            return _stateMachines.filterIsInstance(klass).map { it to (it.psm as ProtocolStateMachine<T>).resultFuture }
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
        val loggerName: String,
        val awaitingTopic: String,
        val awaitingObjectOfType: String   // java class name
    )

    init {
        // Blank out the default uncaught exception handler because we always catch things ourselves, and the default
        // just redundantly prints stack traces to the logs.
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->  }

        if (checkpointing)
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
            _stateMachines.add(psm.logic)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            val topic = checkpoint.awaitingTopic

            // And now re-wire the deserialised continuation back up to the network service.
            serviceHub.networkService.runOnNextMessage(topic, runInThread) { netMsg ->
                val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), awaitingObjectOfType)
                logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
                iterateStateMachine(psm, serviceHub.networkService, logger, obj, checkpointKey) {
                    try {
                        Fiber.unparkDeserialized(it, SameThreadFiberScheduler)
                    } catch(e: Throwable) {
                        logError(e, logger, obj, topic, it)
                    }
                }
            }
        }
    }

    private fun logError(e: Throwable, logger: Logger, obj: Any, topic: String, psm: ProtocolStateMachine<*>) {
        logger.error("Protocol state machine ${psm.javaClass.name} threw '${Throwables.getRootCause(e)}' " +
                "when handling a message of type ${obj.javaClass.name} on topic $topic")
        if (logger.isTraceEnabled) {
            val s = StringWriter()
            Throwables.getRootCause(e).printStackTrace(PrintWriter(s))
            logger.trace("Stack trace of protocol error is: $s")
        }
    }

    private fun deserializeFiber(bits: ByteArray): ProtocolStateMachine<*> {
        val deserializer = Fiber.getFiberSerializer() as KryoSerializer
        val kryo = createKryo(deserializer.kryo)
        val psm = kryo.readClassAndObject(Input(bits)) as ProtocolStateMachine<*>
        return psm
    }

    /**
     * Kicks off a brand new state machine of the given class. It will log with the named logger.
     * The state machine will be persisted when it suspends, with automated restart if the StateMachineManager is
     * restarted with checkpointed state machines in the storage service.
     */
    fun <T> add(loggerName: String, logic: ProtocolLogic<T>): ListenableFuture<T> {
        val logger = LoggerFactory.getLogger(loggerName)
        val fiber = ProtocolStateMachine(logic)
        iterateStateMachine(fiber, serviceHub.networkService, logger, null, null) {
            it.start()
        }
        return fiber.resultFuture
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

    private fun iterateStateMachine(psm: ProtocolStateMachine<*>, net: MessagingService, logger: Logger,
                                    obj: Any?, prevCheckpointKey: SecureHash?, resumeFunc: (ProtocolStateMachine<*>) -> Unit) {
        val onSuspend = fun(request: FiberRequest, serFiber: ByteArray) {
            // We have a request to do something: send, receive, or send-and-receive.
            if (request is FiberRequest.ExpectingResponse<*>) {
                // Prepare a listener on the network that runs in the background thread when we received a message.
                checkpointAndSetupMessageHandler(logger, net, psm, request.responseType,
                        "${request.topic}.${request.sessionIDForReceive}", prevCheckpointKey, serFiber)
            }
            // If an object to send was provided (not null), send it now.
            request.obj?.let {
                val topic = "${request.topic}.${request.sessionIDForSend}"
                logger.trace { "-> ${request.destination}/$topic : message of type ${it.javaClass.name}" }
                net.send(net.createMessage(topic, it.serialize().bits), request.destination!!)
            }
            if (request is FiberRequest.NotExpectingResponse) {
                // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
                iterateStateMachine(psm, net, logger, null, prevCheckpointKey) {
                    try {
                        Fiber.unpark(it, QUASAR_UNBLOCKER)
                    } catch(e: Throwable) {
                        logError(e, logger, request.obj!!, request.topic, it)
                    }
                }
            }
        }

        psm.prepareForResumeWith(serviceHub, obj, logger, onSuspend)

        resumeFunc(psm)

        // We're back! Check if the fiber is finished and if so, clean up.
        if (psm.isTerminated) {
            _stateMachines.remove(psm.logic)
            checkpointsMap.remove(prevCheckpointKey)
        }
    }

    private fun checkpointAndSetupMessageHandler(logger: Logger, net: MessagingService, psm: ProtocolStateMachine<*>,
                                                 responseType: Class<*>, topic: String, prevCheckpointKey: SecureHash?,
                                                 serialisedFiber: ByteArray) {
        val checkpoint = Checkpoint(serialisedFiber, logger.name, topic, responseType.name)
        val curPersistedBytes = checkpoint.serialize().bits
        if (checkpointing)
            persistCheckpoint(prevCheckpointKey, curPersistedBytes)
        val newCheckpointKey = curPersistedBytes.sha256()
        net.runOnNextMessage(topic, runInThread) { netMsg ->
            val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), responseType)
            logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
            iterateStateMachine(psm, net, logger, obj, newCheckpointKey) {
                try {
                    Fiber.unpark(it, QUASAR_UNBLOCKER)
                } catch(e: Throwable) {
                    logError(e, logger, obj, topic, it)
                }
            }
        }
    }
}

object SameThreadFiberScheduler : FiberExecutorScheduler("Same thread scheduler", MoreExecutors.directExecutor())

/**
 * A sub-class of [ProtocolLogic<T>] implements a protocol flow using direct, straight line blocking code. Thus you
 * can write complex protocol logic in an ordinary fashion, without having to think about callbacks, restarting after
 * a node crash, how many instances of your protocol there are running and so on.
 *
 * Invoking the network will cause the call stack to be suspended onto the heap and then serialized to a database using
 * the Quasar fibers framework. Because of this, if you need access to data that might change over time, you should
 * request it just-in-time via the [serviceHub] property which is provided. Don't try and keep data you got from a
 * service across calls to send/receive/sendAndReceive because the world might change in arbitrary ways out from
 * underneath you, for instance, if the node is restarted or reconfigured!
 *
 * Additionally, be aware of what data you pin either via the stack or in your [ProtocolLogic] implementation. Very large
 * objects or datasets will hurt performance by increasing the amount of data stored in each checkpoint.
 *
 * If you'd like to use another ProtocolLogic class as a component of your own, construct it on the fly and then pass
 * it to the [subProtocol] method. It will return the result of that protocol when it completes.
 */
abstract class ProtocolLogic<T> {
    /** Reference to the [Fiber] instance that is the top level controller for the entire flow. */
    lateinit var psm: ProtocolStateMachine<*>

    /** This is where you should log things to. */
    val logger: Logger get() = psm.logger
    /** Provides access to big, heavy classes that may be reconstructed from time to time, e.g. across restarts */
    val serviceHub: ServiceHub get() = psm.serviceHub

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long,
                                                sessionIDForReceive: Long, obj: Any): UntrustworthyData<T> {
        return psm.sendAndReceive(topic, destination, sessionIDForSend, sessionIDForReceive, obj, T::class.java)
    }
    inline fun <reified T : Any> receive(topic: String, sessionIDForReceive: Long): UntrustworthyData<T> {
        return psm.receive(topic, sessionIDForReceive, T::class.java)
    }
    @Suspendable fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any) {
        psm.send(topic, destination, sessionID, obj)
    }

    /**
     * Invokes the given subprotocol by simply passing through this [ProtocolLogic]s reference to the
     * [ProtocolStateMachine] and then calling the [call] method.
     */
    @Suspendable fun <R> subProtocol(subLogic: ProtocolLogic<R>): R {
        subLogic.psm = psm
        return subLogic.call()
    }

    @Suspendable
    abstract fun call(): T
}

/**
 * A ProtocolStateMachine instance is a suspendable fiber that delegates all actual logic to a [ProtocolLogic] instance.
 * For any given flow there is only one PSM, even if that protocol invokes subprotocols.
 *
 * These classes are created by the [StateMachineManager] when a new protocol is started at the topmost level. If
 * a protocol invokes a sub-protocol, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
class ProtocolStateMachine<R>(val logic: ProtocolLogic<R>) : Fiber<R>("protocol", SameThreadFiberScheduler) {
    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient private var suspendFunc: ((result: FiberRequest, serFiber: ByteArray) -> Unit)? = null
    @Transient private var resumeWithObject: Any? = null
    @Transient lateinit var serviceHub: ServiceHub
    @Transient lateinit var logger: Logger

    init {
        logic.psm = this
    }

    fun prepareForResumeWith(serviceHub: ServiceHub, withObject: Any?, logger: Logger,
                             suspendFunc: (FiberRequest, ByteArray) -> Unit) {
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
    private fun <T : Any> suspendAndExpectReceive(with: FiberRequest): UntrustworthyData<T> {
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
        return UntrustworthyData(tmp as T)
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    fun <T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long, sessionIDForReceive: Long,
                                 obj: Any, recvType: Class<T>): UntrustworthyData<T> {
        val result = FiberRequest.ExpectingResponse(topic, destination, sessionIDForSend, sessionIDForReceive, obj, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    fun <T : Any> receive(topic: String, sessionIDForReceive: Long, recvType: Class<T>): UntrustworthyData<T> {
        val result = FiberRequest.ExpectingResponse(topic, null, -1, sessionIDForReceive, null, recvType)
        return suspendAndExpectReceive(result)
    }

    @Suspendable
    fun send(topic: String, destination: MessageRecipients, sessionID: Long, obj: Any) {
        val result = FiberRequest.NotExpectingResponse(topic, destination, sessionID, obj)
        Fiber.parkAndSerialize { fiber, writer -> suspendFunc!!(result, writer.write(fiber)) }
    }
}

/**
 * A small utility to approximate taint tracking: if a method gives you back one of these, it means the data came from
 * a remote source that may be incentivised to pass us junk that violates basic assumptions and thus must be checked
 * first. The wrapper helps you to avoid forgetting this vital step. Things you might want to check are:
 *
 * - Is this object the one you actually expected? Did the other side hand you back something technically valid but
 *   not what you asked for?
 * - Is the object disobeying its own invariants?
 * - Are any objects *reachable* from this object mismatched or not what you expected?
 * - Is it suspiciously large or small?
 */
class UntrustworthyData<T>(private val fromUntrustedWorld: T) {
    val data: T
        @Deprecated("Accessing the untrustworthy data directly without validating it first is a bad idea")
        get() = fromUntrustedWorld

    @Suppress("DEPRECATION")
    inline fun <R> validate(validator: (T) -> R) = validator(data)
}

// TODO: Clean this up
open class FiberRequest(val topic: String, val destination: MessageRecipients?,
                        val sessionIDForSend: Long, val sessionIDForReceive: Long, val obj: Any?) {
    class ExpectingResponse<R : Any>(
            topic: String,
            destination: MessageRecipients?,
            sessionIDForSend: Long,
            sessionIDForReceive: Long,
            obj: Any?,
            val responseType: Class<R>
    ) : FiberRequest(topic, destination, sessionIDForSend, sessionIDForReceive, obj)

    class NotExpectingResponse(topic: String, destination: MessageRecipients, sessionIDForSend: Long, obj: Any?)
        : FiberRequest(topic, destination, sessionIDForSend, -1, obj)
}
