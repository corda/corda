package core.messaging

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.io.Input
import com.google.common.base.Throwables
import com.google.common.util.concurrent.ListenableFuture
import core.crypto.SecureHash
import core.crypto.sha256
import core.node.ServiceHub
import core.protocols.ProtocolLogic
import core.protocols.ProtocolStateMachine
import core.serialization.THREAD_LOCAL_KRYO
import core.serialization.createKryo
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.AffinityExecutor
import core.utilities.ProgressTracker
import core.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
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
 * The SMM will always invoke the protocol fibers on the given [AffinityExecutor], regardless of which thread actually
 * starts them via [add].
 *
 * TODO: Session IDs should be set up and propagated automatically, on demand.
 * TODO: Consider the issue of continuation identity more deeply: is it a safe assumption that a serialised
 *       continuation is always unique?
 * TODO: Think about how to bring the system to a clean stop so it can be upgraded without any serialised stacks on disk
 * TODO: Timeouts
 * TODO: Surfacing of exceptions via an API and/or management UI
 * TODO: Ability to control checkpointing explicitly, for cases where you know replaying a message can't hurt
 * TODO: Make Kryo (de)serialize markers for heavy objects that are currently in the service hub. This avoids mistakes
 *       where services are temporarily put on the stack.
 * TODO: Implement stub/skel classes that provide a basic RPC framework on top of this.
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHub, val executor: AffinityExecutor) {
    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    val scheduler = FiberScheduler()

    // This map is backed by a database and will be used to store serialised state machines to disk, so we can resurrect
    // them across node restarts.
    private val checkpointsMap = serviceHub.storageService.stateMachines
    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val stateMachines = Collections.synchronizedList(ArrayList<ProtocolLogic<*>>())

    // Monitoring support.
    private val metrics = serviceHub.monitoringService.metrics

    init {
        metrics.register("Protocols.InFlight", Gauge<kotlin.Int> { stateMachines.size })
    }

    private val checkpointingMeter = metrics.meter("Protocols.Checkpointing Rate")
    private val totalStartedProtocols = metrics.counter("Protocols.Started")
    private val totalFinishedProtocols = metrics.counter("Protocols.Finished")

    /** Returns a list of all state machines executing the given protocol logic at the top level (subprotocols do not count) */
    fun <T> findStateMachines(klass: Class<out ProtocolLogic<T>>): List<Pair<ProtocolLogic<T>, ListenableFuture<T>>> {
        synchronized(stateMachines) {
            @Suppress("UNCHECKED_CAST")
            return stateMachines.filterIsInstance(klass).map { it to (it.psm as ProtocolStateMachine<T>).resultFuture }
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
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
            (fiber as ProtocolStateMachine<*>).logger.error("Caught exception from protocol", throwable)
        }
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
            stateMachines.add(psm.logic)
            val logger = LoggerFactory.getLogger(checkpoint.loggerName)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            val topic = checkpoint.awaitingTopic

            // And now re-wire the deserialised continuation back up to the network service.
            serviceHub.networkService.runOnNextMessage(topic, executor) { netMsg ->
                // TODO: See security note below.
                val obj: Any = THREAD_LOCAL_KRYO.get().readClassAndObject(Input(netMsg.data))
                if (!awaitingObjectOfType.isInstance(obj))
                    throw ClassCastException("Received message of unexpected type: ${obj.javaClass.name} vs ${awaitingObjectOfType.name}")
                logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
                iterateStateMachine(psm, serviceHub.networkService, logger, obj, checkpointKey) {
                    try {
                        Fiber.unparkDeserialized(it, scheduler)
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
        val deserializer = Fiber.getFiberSerializer(false) as KryoSerializer
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
        try {
            val logger = LoggerFactory.getLogger(loggerName)
            val fiber = ProtocolStateMachine(logic, scheduler)
            // Need to add before iterating in case of immediate completion
            stateMachines.add(logic)
            executor.executeASAP {
                iterateStateMachine(fiber, serviceHub.networkService, logger, null, null) {
                    it.start()
                }
                totalStartedProtocols.inc()
            }
            return fiber.resultFuture
        } catch(e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    private fun persistCheckpoint(prevCheckpointKey: SecureHash?, new: ByteArray): SecureHash {
        // It's OK for this to be unsynchronised, as the prev/new byte arrays are specific to a continuation instance,
        // and the underlying map provided by the database layer is expected to be thread safe.
        if (prevCheckpointKey != null)
            checkpointsMap.remove(prevCheckpointKey)
        val key = SecureHash.sha256(new)
        checkpointsMap[key] = new
        checkpointingMeter.mark()
        return key
    }

    private fun iterateStateMachine(psm: ProtocolStateMachine<*>, net: MessagingService, logger: Logger,
                                    obj: Any?, prevCheckpointKey: SecureHash?, resumeFunc: (ProtocolStateMachine<*>) -> Unit) {
        executor.checkOnThread()
        val onSuspend = fun(request: FiberRequest, serFiber: ByteArray) {
            // We have a request to do something: send, receive, or send-and-receive.
            if (request is FiberRequest.ExpectingResponse<*>) {
                // Prepare a listener on the network that runs in the background thread when we received a message.
                checkpointAndSetupMessageHandler(logger, net, psm, request, prevCheckpointKey, serFiber)
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
            psm.logic.progressTracker?.currentStep = ProgressTracker.DONE
            stateMachines.remove(psm.logic)
            checkpointsMap.remove(prevCheckpointKey)
            totalFinishedProtocols.inc()
        }
    }

    private fun checkpointAndSetupMessageHandler(logger: Logger, net: MessagingService, psm: ProtocolStateMachine<*>,
                                                 request: FiberRequest.ExpectingResponse<*>, prevCheckpointKey: SecureHash?,
                                                 serialisedFiber: ByteArray) {
        executor.checkOnThread()
        val topic = "${request.topic}.${request.sessionIDForReceive}"
        val checkpoint = Checkpoint(serialisedFiber, logger.name, topic, request.responseType.name)
        val curPersistedBytes = checkpoint.serialize().bits
        persistCheckpoint(prevCheckpointKey, curPersistedBytes)
        val newCheckpointKey = curPersistedBytes.sha256()
        logger.trace { "Waiting for message of type ${request.responseType.name} on $topic" }
        val consumed = AtomicBoolean()
        net.runOnNextMessage(topic, executor) { netMsg ->
            // Some assertions to ensure we don't execute on the wrong thread or get executed more than once.
            executor.checkOnThread()
            check(netMsg.topic == topic) { "Topic mismatch: ${netMsg.topic} vs $topic" }
            check(!consumed.getAndSet(true))
            // TODO: This is insecure: we should not deserialise whatever we find and *then* check.
            //
            // We should instead verify as we read the data that it's what we are expecting and throw as early as
            // possible. We only do it this way for convenience during the prototyping stage. Note that this means
            // we could simply not require the programmer to specify the expected return type at all, and catch it
            // at the last moment when we do the downcast. However this would make protocol code harder to read and
            // make it more difficult to migrate to a more explicit serialisation scheme later.
            val obj: Any = THREAD_LOCAL_KRYO.get().readClassAndObject(Input(netMsg.data))
            if (!request.responseType.isInstance(obj))
                throw IllegalStateException("Expected message of type ${request.responseType.name} but got ${obj.javaClass.name}", request.stackTraceInCaseOfProblems)
            iterateStateMachine(psm, net, logger, obj, newCheckpointKey) {
                try {
                    Fiber.unpark(it, QUASAR_UNBLOCKER)
                } catch(e: Throwable) {
                    logError(e, logger, obj, topic, it)
                }
            }
        }
    }

    // TODO: Clean this up
    open class FiberRequest(val topic: String, val destination: MessageRecipients?,
                            val sessionIDForSend: Long, val sessionIDForReceive: Long, val obj: Any?) {
        // This is used to identify where we suspended, in case of message mismatch errors and other things where we
        // don't have the original stack trace because it's in a suspended fiber.
        val stackTraceInCaseOfProblems = StackSnapshot()

        class ExpectingResponse<R : Any>(
                topic: String,
                destination: MessageRecipients?,
                sessionIDForSend: Long,
                sessionIDForReceive: Long,
                obj: Any?,
                val responseType: Class<R>
        ) : FiberRequest(topic, destination, sessionIDForSend, sessionIDForReceive, obj)

        class NotExpectingResponse(
                topic: String,
                destination: MessageRecipients,
                sessionIDForSend: Long,
                obj: Any?
        ) : FiberRequest(topic, destination, sessionIDForSend, -1, obj)
    }
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")