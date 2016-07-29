package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.Kryo
import com.google.common.base.Throwables
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.abbreviate
import com.r3corda.core.messaging.TopicSession
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.messaging.send
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.*
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.Checkpoint
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.node.utilities.AffinityExecutor
import rx.Observable
import rx.subjects.PublishSubject
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.Collections.synchronizedMap
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
class StateMachineManager(val serviceHub: ServiceHubInternal, tokenizableServices: List<Any>, val checkpointStorage: CheckpointStorage, val executor: AffinityExecutor) {
    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    val scheduler = FiberScheduler()

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val stateMachines = synchronizedMap(LinkedHashMap<ProtocolStateMachineImpl<*>, Checkpoint>())

    // Monitoring support.
    private val metrics = serviceHub.monitoringService.metrics

    init {
        metrics.register("Protocols.InFlight", Gauge<Int> { stateMachines.size })
    }

    private val checkpointingMeter = metrics.meter("Protocols.Checkpointing Rate")
    private val totalStartedProtocols = metrics.counter("Protocols.Started")
    private val totalFinishedProtocols = metrics.counter("Protocols.Finished")

    // Context for tokenized services in checkpoints
    private val serializationContext = SerializeAsTokenContext(tokenizableServices, quasarKryo())

    /** Returns a list of all state machines executing the given protocol logic at the top level (subprotocols do not count) */
    fun <P : ProtocolLogic<T>, T> findStateMachines(protocolClass: Class<P>): List<Pair<P, ListenableFuture<T>>> {
        synchronized(stateMachines) {
            @Suppress("UNCHECKED_CAST")
            return stateMachines.keys
                    .map { it.logic }
                    .filterIsInstance(protocolClass)
                    .map { it to (it.psm as ProtocolStateMachineImpl<T>).resultFuture }
        }
    }

    val allStateMachines: List<ProtocolLogic<*>>
        get() = stateMachines.keys.map { it.logic }

    private val _changesPublisher = PublishSubject.create<Triple<ProtocolLogic<*>, AddOrRemove, Long>>()

    /**
     * An observable that emits triples of the changing protocol, the type of change, and a process-specific ID number
     * which may change across restarts.
     */
    val changes: Observable<Triple<ProtocolLogic<*>, AddOrRemove, Long>>
        get() = _changesPublisher

    private fun notifyChangeObservers(psm: ProtocolStateMachineImpl<*>, change: AddOrRemove) {
        _changesPublisher.onNext(Triple(psm.logic, change, psm.id))
    }

    // Used to work around a small limitation in Quasar.
    private val QUASAR_UNBLOCKER = run {
        val field = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
        field.isAccessible = true
        field.get(null)
    }

    init {
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
            (fiber as ProtocolStateMachineImpl<*>).logger.error("Caught exception from protocol", throwable)
        }
    }

    fun start() {
        checkpointStorage.checkpoints.forEach { restoreFromCheckpoint(it) }
    }

    private fun restoreFromCheckpoint(checkpoint: Checkpoint) {
        val fiber = deserializeFiber(checkpoint.serialisedFiber)
        initFiber(fiber, { checkpoint })

        when (checkpoint.request) {
            is FiberRequest.ExpectingResponse<*> -> {
                val topic = checkpoint.request.receiveTopicSession
                val awaitingPayloadType = checkpoint.request.responseType
                fiber.logger.info("Restored ${fiber.logic} - it was previously waiting for message of type ${awaitingPayloadType.name} on topic $topic")
                iterateOnResponse(fiber, awaitingPayloadType, checkpoint.serialisedFiber, checkpoint.request) {
                    try {
                        Fiber.unparkDeserialized(fiber, scheduler)
                    } catch (e: Throwable) {
                        logError(e, it, topic, fiber)
                    }
                }
            }
            is FiberRequest.NotExpectingResponse -> restoreNotExpectingResponse(fiber, checkpoint.request)
            null -> restoreNotExpectingResponse(fiber)
            else -> throw IllegalStateException("Unknown fiber request type " + checkpoint.request)
        }
    }

    /**
     * Restore a Fiber which was not expecting a response (either because it hasn't asked for one, such as a new
     * Fiber, or because the detail it needed has arrived).
     */
    private fun restoreNotExpectingResponse(fiber: ProtocolStateMachineImpl<*>, request: FiberRequest? = null) {
        val payload = request?.payload
        fiber.logger.info("Restored ${fiber.logic} - it was not waiting on any message; received payload: ${payload.toString().abbreviate(50)}")
        executor.executeASAP {
            iterateStateMachine(fiber, payload) {
                try {
                    Fiber.unparkDeserialized(fiber, scheduler)
                } catch (e: Throwable) {
                    logError(e, it, null, fiber)
                }
            }
        }
    }

    private fun serializeFiber(fiber: ProtocolStateMachineImpl<*>): SerializedBytes<ProtocolStateMachineImpl<*>> {
        // We don't use the passed-in serializer here, because we need to use our own augmented Kryo.
        val kryo = quasarKryo()
        // add the map of tokens -> tokenizedServices to the kyro context
        SerializeAsTokenSerializer.setContext(kryo, serializationContext)
        return fiber.serialize(kryo)
    }

    private fun deserializeFiber(serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>): ProtocolStateMachineImpl<*> {
        val kryo = quasarKryo()
        // put the map of token -> tokenized into the kryo context
        SerializeAsTokenSerializer.setContext(kryo, serializationContext)
        return serialisedFiber.deserialize(kryo)
    }

    private fun quasarKryo(): Kryo {
        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
        return createKryo(serializer.kryo)
    }

    private fun logError(e: Throwable, payload: Any?, topicSession: TopicSession?, psm: ProtocolStateMachineImpl<*>) {
        psm.logger.error("Protocol state machine ${psm.javaClass.name} threw '${Throwables.getRootCause(e)}' " +
                "when handling a message of type ${payload?.javaClass?.name} on queue $topicSession")
        if (psm.logger.isTraceEnabled) {
            val s = StringWriter()
            Throwables.getRootCause(e).printStackTrace(PrintWriter(s))
            psm.logger.trace("Stack trace of protocol error is: $s")
        }
    }

    private fun initFiber(psm: ProtocolStateMachineImpl<*>, startingCheckpoint: () -> Checkpoint) {
        psm.serviceHub = serviceHub
        psm.suspendAction = { request ->
            psm.logger.trace { "Suspended fiber ${psm.id} ${psm.logic}" }
            onNextSuspend(psm, request)
        }
        psm.actionOnEnd = {
            psm.logic.progressTracker?.currentStep = ProgressTracker.DONE
            val finalCheckpoint = stateMachines.remove(psm)
            if (finalCheckpoint != null) {
                checkpointStorage.removeCheckpoint(finalCheckpoint)
            }
            totalFinishedProtocols.inc()
            notifyChangeObservers(psm, AddOrRemove.REMOVE)
        }
        stateMachines[psm] = startingCheckpoint()
        notifyChangeObservers(psm, AddOrRemove.ADD)
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
            initFiber(fiber) {
                val checkpoint = Checkpoint(serializeFiber(fiber), null)
                checkpointStorage.addCheckpoint(checkpoint)
                checkpoint
            }
            executor.executeASAP {
                iterateStateMachine(fiber, null) {
                    fiber.start()
                }
                totalStartedProtocols.inc()
            }
            return fiber.resultFuture
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    private fun updateCheckpoint(psm: ProtocolStateMachineImpl<*>,
                                 serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>,
                                 request: FiberRequest) {
        val newCheckpoint = Checkpoint(serialisedFiber, request)
        val previousCheckpoint = stateMachines.put(psm, newCheckpoint)
        if (previousCheckpoint != null) {
            checkpointStorage.removeCheckpoint(previousCheckpoint)
        }
        checkpointStorage.addCheckpoint(newCheckpoint)
        checkpointingMeter.mark()
    }

    private fun iterateStateMachine(psm: ProtocolStateMachineImpl<*>,
                                    receivedPayload: Any?,
                                    resumeAction: (Any?) -> Unit) {
        executor.checkOnThread()
        psm.receivedPayload = receivedPayload
        psm.logger.trace { "Waking up fiber ${psm.id} ${psm.logic}" }
        resumeAction(receivedPayload)
    }

    private fun onNextSuspend(psm: ProtocolStateMachineImpl<*>, request: FiberRequest) {
        // We have a request to do something: send, receive, or send-and-receive.
        when (request) {
            is FiberRequest.ExpectingResponse<*> -> {
                // Prepare a listener on the network that runs in the background thread when we receive a message.
                checkpointOnExpectingResponse(psm, request)
            }
        }
        // If a non-null payload to send was provided, send it now.
        val queueID = TopicSession(request.topic, request.sessionIDForSend)
        request.payload?.let {
            psm.logger.trace { "Sending message of type ${it.javaClass.name} using queue $queueID to ${request.destination} (${it.toString().abbreviate(50)})" }
            val node = serviceHub.networkMapCache.getNodeByLegalName(request.destination!!.name)
            if (node == null) {
                throw IllegalArgumentException("Don't know about ${request.destination} but trying to send a message of type ${it.javaClass.name} on $queueID (${it.toString().abbreviate(50)})", request.stackTraceInCaseOfProblems)
            }
            serviceHub.networkService.send(queueID, it, node.address)
        }
        if (request is FiberRequest.NotExpectingResponse) {
            // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
            iterateStateMachine(psm, null) {
                try {
                    Fiber.unpark(psm, QUASAR_UNBLOCKER)
                } catch(e: Throwable) {
                    logError(e, request.payload, queueID, psm)
                }
            }
        }
    }

    private fun checkpointOnExpectingResponse(psm: ProtocolStateMachineImpl<*>, request: FiberRequest.ExpectingResponse<*>) {
        executor.checkOnThread()
        val queueID = request.receiveTopicSession
        val serialisedFiber = serializeFiber(psm)
        updateCheckpoint(psm, serialisedFiber, request)
        psm.logger.trace { "Preparing to receive message of type ${request.responseType.name} on queue $queueID" }
        iterateOnResponse(psm, request.responseType, serialisedFiber, request) {
            try {
                Fiber.unpark(psm, QUASAR_UNBLOCKER)
            } catch(e: Throwable) {
                logError(e, it, queueID, psm)
            }
        }
    }

    /**
     * Add a trigger to the [MessagingService] to deserialize the fiber and pass message content to it, once a message is
     * received.
     */
    private fun iterateOnResponse(psm: ProtocolStateMachineImpl<*>,
                                  responseType: Class<*>,
                                  serialisedFiber: SerializedBytes<ProtocolStateMachineImpl<*>>,
                                  request: FiberRequest.ExpectingResponse<*>,
                                  resumeAction: (Any?) -> Unit) {
        val topic = request.receiveTopicSession
        serviceHub.networkService.runOnNextMessage(topic, executor) { netMsg ->
            // Assertion to ensure we don't execute on the wrong thread.
            executor.checkOnThread()
            // TODO: This is insecure: we should not deserialise whatever we find and *then* check.
            // We should instead verify as we read the data that it's what we are expecting and throw as early as
            // possible. We only do it this way for convenience during the prototyping stage. Note that this means
            // we could simply not require the programmer to specify the expected return type at all, and catch it
            // at the last moment when we do the downcast. However this would make protocol code harder to read and
            // make it more difficult to migrate to a more explicit serialisation scheme later.
            val payload = netMsg.data.deserialize<Any>()
            check(responseType.isInstance(payload)) { "Expected message of type ${responseType.name} but got ${payload.javaClass.name}" }
            // Update the fiber's checkpoint so that it's no longer waiting on a response, but rather has the received payload
            updateCheckpoint(psm, serialisedFiber, request)
            psm.logger.trace { "Received message of type ${payload.javaClass.name} on topic ${request.topic}.${request.sessionIDForReceive} (${payload.toString().abbreviate(50)})" }
            iterateStateMachine(psm, payload, resumeAction)
        }
    }
}

class StackSnapshot : Throwable("This is a stack trace to help identify the source of the underlying problem")
