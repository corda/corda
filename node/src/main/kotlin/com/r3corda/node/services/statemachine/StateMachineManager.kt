package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.io.Input
import com.google.common.base.Throwables
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.messaging.send
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolStateMachine
import com.r3corda.core.serialization.*
import com.r3corda.core.then
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.Checkpoint
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.AffinityExecutor
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.Collections.synchronizedMap
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
 * TODO: Implement stub/skel classes that provide a basic RPC framework on top of this.
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHubInternal, val checkpointStorage: CheckpointStorage, val executor: AffinityExecutor) {
    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    val scheduler = FiberScheduler()

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val stateMachines = synchronizedMap(HashMap<ProtocolStateMachineImpl<*>, Checkpoint>())

    // Monitoring support.
    private val metrics = serviceHub.monitoringService.metrics

    init {
        metrics.register("Protocols.InFlight", Gauge<Int> { stateMachines.size })
    }

    private val checkpointingMeter = metrics.meter("Protocols.Checkpointing Rate")
    private val totalStartedProtocols = metrics.counter("Protocols.Started")
    private val totalFinishedProtocols = metrics.counter("Protocols.Finished")

    // Context for tokenized services in checkpoints
    private val serializationContext = SerializeAsTokenContext(serviceHub)

    /** Returns a list of all state machines executing the given protocol logic at the top level (subprotocols do not count) */
    fun <T> findStateMachines(klass: Class<out ProtocolLogic<T>>): List<Pair<ProtocolLogic<T>, ListenableFuture<T>>> {
        synchronized(stateMachines) {
            @Suppress("UNCHECKED_CAST")
            return stateMachines.keys
                    .map { it.logic }
                    .filterIsInstance(klass)
                    .map { it to (it.psm as ProtocolStateMachineImpl<T>).resultFuture }
        }
    }

    // Used to work around a small limitation in Quasar.
    private val QUASAR_UNBLOCKER = run {
        val field = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
        field.isAccessible = true
        field.get(null)
    }

    companion object {
        var restoreCheckpointsOnStart = true
    }

    init {
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
            (fiber as ProtocolStateMachineImpl<*>).logger.error("Caught exception from protocol", throwable)
        }
        if (restoreCheckpointsOnStart)
            restoreCheckpoints()
    }

    /** Reads the database map and resurrects any serialised state machines. */
    private fun restoreCheckpoints() {
        for (checkpoint in checkpointStorage.checkpoints) {
            // Grab the Kryo engine configured by Quasar for its own stuff, and then do our own configuration on top
            // so we can deserialised the nested stream that holds the fiber.
            val psm = deserializeFiber(checkpoint.serialisedFiber)
            initFiber(psm, checkpoint)
            val awaitingObjectOfType = Class.forName(checkpoint.awaitingObjectOfType)
            val topic = checkpoint.awaitingTopic

            psm.logger.info("restored ${psm.logic} - was previously awaiting on topic $topic")

            // And now re-wire the deserialised continuation back up to the network service.
            serviceHub.networkService.runOnNextMessage(topic, executor) { netMsg ->
                // TODO: See security note below.
                val obj: Any = THREAD_LOCAL_KRYO.get().readClassAndObject(Input(netMsg.data))
                if (!awaitingObjectOfType.isInstance(obj))
                    throw ClassCastException("Received message of unexpected type: ${obj.javaClass.name} vs ${awaitingObjectOfType.name}")
                psm.logger.trace { "<- $topic : message of type ${obj.javaClass.name}" }
                iterateStateMachine(psm, obj) {
                    try {
                        Fiber.unparkDeserialized(it, scheduler)
                    } catch(e: Throwable) {
                        logError(e, obj, topic, it)
                    }
                }
            }
        }
    }

    private fun deserializeFiber(serialisedFiber: SerializedBytes<out ProtocolStateMachine<*>>): ProtocolStateMachineImpl<*> {
        val deserializer = Fiber.getFiberSerializer(false) as KryoSerializer
        val kryo = createKryo(deserializer.kryo)
        // put the map of token -> tokenized into the kryo context
        SerializeAsTokenSerializer.setContext(kryo, serializationContext)
        return serialisedFiber.deserialize(kryo) as ProtocolStateMachineImpl<*>
    }

    private fun logError(e: Throwable, obj: Any, topic: String, psm: ProtocolStateMachineImpl<*>) {
        psm.logger.error("Protocol state machine ${psm.javaClass.name} threw '${Throwables.getRootCause(e)}' " +
                "when handling a message of type ${obj.javaClass.name} on topic $topic")
        if (psm.logger.isTraceEnabled) {
            val s = StringWriter()
            Throwables.getRootCause(e).printStackTrace(PrintWriter(s))
            psm.logger.trace("Stack trace of protocol error is: $s")
        }
    }

    private fun initFiber(psm: ProtocolStateMachineImpl<*>, checkpoint: Checkpoint?) {
        stateMachines[psm] = checkpoint
        psm.resultFuture.then(executor) {
            psm.logic.progressTracker?.currentStep = ProgressTracker.DONE
            val finalCheckpoint = stateMachines.remove(psm)
            if (finalCheckpoint != null) {
                checkpointStorage.removeCheckpoint(finalCheckpoint)
            }
            totalFinishedProtocols.inc()
        }
    }

    /**
     * Kicks off a brand new state machine of the given class. It will log with the named logger.
     * The state machine will be persisted when it suspends, with automated restart if the StateMachineManager is
     * restarted with checkpointed state machines in the storage service.
     */
    fun <T> add(loggerName: String, logic: ProtocolLogic<T>): ListenableFuture<T> {
        try {
            val fiber = ProtocolStateMachineImpl(logic, scheduler, loggerName)
            // Need to add before iterating in case of immediate completion
            initFiber(fiber, null)
            executor.executeASAP {
                iterateStateMachine(fiber, null) {
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

    private fun replaceCheckpoint(psm: ProtocolStateMachineImpl<*>, newCheckpoint: Checkpoint) {
        // It's OK for this to be unsynchronised, as the prev/new byte arrays are specific to a continuation instance,
        // and the underlying map provided by the database layer is expected to be thread safe.
        val previousCheckpoint = stateMachines.put(psm, newCheckpoint)
        if (previousCheckpoint != null) {
            checkpointStorage.removeCheckpoint(previousCheckpoint)
        }
        checkpointStorage.addCheckpoint(newCheckpoint)
        checkpointingMeter.mark()
    }

    private fun iterateStateMachine(psm: ProtocolStateMachineImpl<*>,
                                    obj: Any?,
                                    resumeFunc: (ProtocolStateMachineImpl<*>) -> Unit) {
        executor.checkOnThread()
        val onSuspend = fun(request: FiberRequest, fiber: ProtocolStateMachineImpl<*>) {
            // We have a request to do something: send, receive, or send-and-receive.
            if (request is FiberRequest.ExpectingResponse<*>) {
                // We don't use the passed-in serializer here, because we need to use our own augmented Kryo.
                val deserializer = Fiber.getFiberSerializer(false) as KryoSerializer
                val kryo = createKryo(deserializer.kryo)
                // add the map of tokens -> tokenizedServices to the kyro context
                SerializeAsTokenSerializer.setContext(kryo, serializationContext)
                val serialisedFiber = fiber.serialize(kryo)
                // Prepare a listener on the network that runs in the background thread when we received a message.
                checkpointAndSetupMessageHandler(psm, request, serialisedFiber)
            }
            // If an object to send was provided (not null), send it now.
            request.obj?.let {
                val topic = "${request.topic}.${request.sessionIDForSend}"
                psm.logger.trace { "-> ${request.destination}/$topic : message of type ${it.javaClass.name}" }
                serviceHub.networkService.send(topic, it, request.destination!!)
            }
            if (request is FiberRequest.NotExpectingResponse) {
                // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
                iterateStateMachine(psm, null) {
                    try {
                        Fiber.unpark(it, QUASAR_UNBLOCKER)
                    } catch(e: Throwable) {
                        logError(e, request.obj!!, request.topic, it)
                    }
                }
            }
        }

        psm.prepareForResumeWith(serviceHub, obj, onSuspend)

        resumeFunc(psm)
    }

    private fun checkpointAndSetupMessageHandler(psm: ProtocolStateMachineImpl<*>,
                                                 request: FiberRequest.ExpectingResponse<*>,
                                                 serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>) {
        executor.checkOnThread()
        val topic = "${request.topic}.${request.sessionIDForReceive}"
        val newCheckpoint = Checkpoint(serialisedFiber, topic, request.responseType.name)
        replaceCheckpoint(psm, newCheckpoint)
        psm.logger.trace { "Waiting for message of type ${request.responseType.name} on $topic" }
        val consumed = AtomicBoolean()
        serviceHub.networkService.runOnNextMessage(topic, executor) { netMsg ->
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
            iterateStateMachine(psm, obj) {
                try {
                    Fiber.unpark(it, QUASAR_UNBLOCKER)
                } catch(e: Throwable) {
                    logError(e, obj, topic, it)
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
