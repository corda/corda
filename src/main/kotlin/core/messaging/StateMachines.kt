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
import java.util.*
import java.util.concurrent.Callable
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
 * TODO: The framework should propagate exceptions and handle error handling automatically.
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
    private val _stateMachines = Collections.synchronizedList(ArrayList<ProtocolStateMachine<*>>())

    // This is a workaround for something Gradle does to us during unit tests. It replaces stderr with its own
    // class that inserts itself into a ThreadLocal. That then gets caught in fiber serialisation, which we don't
    // want because it can't get recreated properly. It turns out there's no good workaround for this! All the obvious
    // approaches fail. Pending resolution of https://github.com/puniverse/quasar/issues/153 we just disable
    // checkpointing when unit tests are run inside Gradle. The right fix is probably to make Quasar's
    // bit-too-clever-for-its-own-good ThreadLocal serialisation trick. It already wasted far more time than it can
    // ever recover.
    val checkpointing: Boolean get() = !System.err.javaClass.name.contains("LinePerThreadBufferingOutputStream")

    /** Returns a snapshot of the currently registered state machines. */
    val stateMachines: List<ProtocolStateMachine<*>> get() {
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
        val loggerName: String,
        val awaitingTopic: String,
        val awaitingObjectOfType: String   // java class name
    )

    init {
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
            _stateMachines.add(psm)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            val topic = checkpoint.awaitingTopic

            // And now re-wire the deserialised continuation back up to the network service.
            serviceHub.networkService.runOnNextMessage(topic, runInThread) { netMsg ->
                val obj: Any = THREAD_LOCAL_KRYO.get().readObject(Input(netMsg.data), awaitingObjectOfType)
                logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
                iterateStateMachine(psm, serviceHub.networkService, logger, obj, checkpointKey) {
                    Fiber.unparkDeserialized(it, SameThreadFiberScheduler)
                }
            }
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
    fun <T : ProtocolStateMachine<*>> add(loggerName: String, fiber: T): T {
        val logger = LoggerFactory.getLogger(loggerName)
        iterateStateMachine(fiber, serviceHub.networkService, logger, null, null) {
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
                    Fiber.unpark(it, QUASAR_UNBLOCKER)
                }
            }
        }

        psm.prepareForResumeWith(serviceHub, obj, logger, onSuspend)

        resumeFunc(psm)

        // We're back! Check if the fiber is finished and if so, clean up.
        if (psm.isTerminated) {
            _stateMachines.remove(psm)
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
                Fiber.unpark(it, QUASAR_UNBLOCKER)
            }
        }
    }
}

object SameThreadFiberScheduler : FiberExecutorScheduler("Same thread scheduler", MoreExecutors.directExecutor())

/**
 * The base class that should be used by any object that wishes to act as a protocol state machine. A PSM is
 * a kind of "fiber", and a fiber in turn is a bit like a thread, but a thread that can be suspended to the heap,
 * serialised to disk, and resumed on demand.
 *
 * Sub-classes should override the [call] method and return whatever the final result of the protocol is. Inside the
 * call method, the rules of normal object oriented programming are a little different:
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
 *
 * Note that the result of the [call] method can be obtained in a couple of different ways. One is to call the get
 * method, as the PSM is a [Future]. But that will block the calling thread until the result is ready, which may not
 * be what you want (unless you know it's finished already). So you can also use the [resultFuture] property, which is
 * a [ListenableFuture] and will let you register a callback.
 *
 * Once created, a PSM should be passed to a [StateMachineManager] which will start it and manage its execution.
 */
abstract class ProtocolStateMachine<R> : Fiber<R>("protocol", SameThreadFiberScheduler), Callable<R> {
    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient private var suspendFunc: ((result: FiberRequest, serFiber: ByteArray) -> Unit)? = null
    @Transient private var resumeWithObject: Any? = null
    @Transient lateinit var serviceHub: ServiceHub
    @Transient protected lateinit var logger: Logger
    @Transient private var _resultFuture: SettableFuture<R>? = SettableFuture.create<R>()

    /** This future will complete when the call method returns. */
    val resultFuture: ListenableFuture<R> get() {
        return _resultFuture ?: run {
            val f = SettableFuture.create<R>()
            _resultFuture = f
            return f
        }
    }

    fun prepareForResumeWith(serviceHub: ServiceHub, withObject: Any?, logger: Logger,
                             suspendFunc: (FiberRequest, ByteArray) -> Unit) {
        this.suspendFunc = suspendFunc
        this.logger = logger
        this.resumeWithObject = withObject
        this.serviceHub = serviceHub

        setUncaughtExceptionHandler { strand, throwable ->
            logger.error("Caught error whilst running protocol state machine ${strand.javaClass.name}", throwable)
        }
    }

    // This line may look useless, but it's needed to convince the Quasar bytecode rewriter to do the right thing.
    @Suspendable override abstract fun call(): R

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun run(): R {
        val result = call()
        if (result != null)
            (resultFuture as SettableFuture<R>).set(result)
        return result
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

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(topic: String, destination: MessageRecipients, sessionIDForSend: Long,
                                                sessionIDForReceive: Long, obj: Any): UntrustworthyData<T> {
        return sendAndReceive(topic, destination, sessionIDForSend, sessionIDForReceive, obj, T::class.java)
    }
    inline fun <reified T : Any> receive(topic: String, sessionIDForReceive: Long): UntrustworthyData<T> {
        return receive(topic, sessionIDForReceive, T::class.java)
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
