package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import co.paralleluniverse.strands.Strand
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.Kryo
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.support.jdk8.collections.removeIf
import net.corda.core.ThreadBox
import net.corda.core.abbreviate
import net.corda.core.crypto.Party
import net.corda.core.crypto.commonName
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.TopicSession
import net.corda.core.messaging.send
import net.corda.core.random63BitValue
import net.corda.core.serialization.*
import net.corda.core.then
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.bufferUntilDatabaseCommit
import net.corda.node.utilities.isolatedTransaction
import org.apache.activemq.artemis.utils.ReusableLatch
import org.jetbrains.exposed.sql.Database
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import javax.annotation.concurrent.ThreadSafe

/**
 * A StateMachineManager is responsible for coordination and persistence of multiple [FlowStateMachine] objects.
 * Each such object represents an instantiation of a (two-party) flow that has reached a particular point.
 *
 * An implementation of this class will persist state machines to long term storage so they can survive process restarts
 * and, if run with a single-threaded executor, will ensure no two state machines run concurrently with each other
 * (bad for performance, good for programmer mental health!).
 *
 * A "state machine" is a class with a single call method. The call method and any others it invokes are rewritten by
 * a bytecode rewriting engine called Quasar, to ensure the code can be suspended and resumed at any point.
 *
 * The SMM will always invoke the flow fibers on the given [AffinityExecutor], regardless of which thread actually
 * starts them via [add].
 *
 * TODO: Consider the issue of continuation identity more deeply: is it a safe assumption that a serialised
 *       continuation is always unique?
 * TODO: Think about how to bring the system to a clean stop so it can be upgraded without any serialised stacks on disk
 * TODO: Timeouts
 * TODO: Surfacing of exceptions via an API and/or management UI
 * TODO: Ability to control checkpointing explicitly, for cases where you know replaying a message can't hurt
 * TODO: Implement stub/skel classes that provide a basic RPC framework on top of this.
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHubInternal,
                          tokenizableServices: List<Any>,
                          val checkpointStorage: CheckpointStorage,
                          val executor: AffinityExecutor,
                          val database: Database) {

    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    companion object {
        private val logger = loggerFor<StateMachineManager>()
        internal val sessionTopic = TopicSession("platform.session")
    }

    val scheduler = FiberScheduler()

    data class Change(
            val logic: FlowLogic<*>,
            val addOrRemove: AddOrRemove,
            val id: StateMachineRunId
    )

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private val mutex = ThreadBox(object {
        var started = false
        val stateMachines = LinkedHashMap<FlowStateMachineImpl<*>, Checkpoint>()
        val changesPublisher = PublishSubject.create<Change>()

        fun notifyChangeObservers(psm: FlowStateMachineImpl<*>, addOrRemove: AddOrRemove) {
            changesPublisher.bufferUntilDatabaseCommit().onNext(Change(psm.logic, addOrRemove, psm.id))
        }
    })

    // True if we're shutting down, so don't resume anything.
    @Volatile private var stopping = false
    // How many Fibers are running and not suspended.  If zero and stopping is true, then we are halted.
    private val liveFibers = ReusableLatch()
    @VisibleForTesting
    val unfinishedFibers = ReusableLatch()

    // Monitoring support.
    private val metrics = serviceHub.monitoringService.metrics

    init {
        metrics.register("Flows.InFlight", Gauge<Int> { mutex.content.stateMachines.size })
    }

    private val checkpointingMeter = metrics.meter("Flows.Checkpointing Rate")
    private val totalStartedFlows = metrics.counter("Flows.Started")
    private val totalFinishedFlows = metrics.counter("Flows.Finished")

    private val openSessions = ConcurrentHashMap<Long, FlowSession>()
    private val recentlyClosedSessions = ConcurrentHashMap<Long, Party>()

    // Context for tokenized services in checkpoints
    private val serializationContext = SerializeAsTokenContext(tokenizableServices, quasarKryo())

    /** Returns a list of all state machines executing the given flow logic at the top level (subflows do not count) */
    fun <P : FlowLogic<T>, T> findStateMachines(flowClass: Class<P>): List<Pair<P, ListenableFuture<T>>> {
        @Suppress("UNCHECKED_CAST")
        return mutex.locked {
            stateMachines.keys
                    .map { it.logic }
                    .filterIsInstance(flowClass)
                    .map { it to (it.fsm as FlowStateMachineImpl<T>).resultFuture }
        }
    }

    val allStateMachines: List<FlowLogic<*>>
        get() = mutex.locked { stateMachines.keys.map { it.logic } }

    /**
     * An observable that emits triples of the changing flow, the type of change, and a process-specific ID number
     * which may change across restarts.
     */
    val changes: Observable<Change>
        get() = mutex.content.changesPublisher

    init {
        Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
            (fiber as FlowStateMachineImpl<*>).logger.error("Caught exception from flow", throwable)
        }
    }

    fun start() {
        restoreFibersFromCheckpoints()
        serviceHub.networkMapCache.mapServiceRegistered.then(executor) { resumeRestoredFibers() }
    }

    private fun decrementLiveFibers() {
        liveFibers.countDown()
    }

    private fun incrementLiveFibers() {
        liveFibers.countUp()
    }

    /**
     * Start the shutdown process, bringing the [StateMachineManager] to a controlled stop.  When this method returns,
     * all Fibers have been suspended and checkpointed, or have completed.
     *
     * @param allowedUnsuspendedFiberCount Optional parameter is used in some tests.
     */
    fun stop(allowedUnsuspendedFiberCount: Int = 0) {
        check(allowedUnsuspendedFiberCount >= 0)
        mutex.locked {
            if (stopping) throw IllegalStateException("Already stopping!")
            stopping = true
        }
        // Account for any expected Fibers in a test scenario.
        liveFibers.countDown(allowedUnsuspendedFiberCount)
        liveFibers.await()
    }

    /**
     * Atomic get snapshot + subscribe. This is needed so we don't miss updates between subscriptions to [changes] and
     * calls to [allStateMachines]
     */
    fun track(): Pair<List<FlowStateMachineImpl<*>>, Observable<Change>> {
        return mutex.locked {
            val bufferedChanges = UnicastSubject.create<Change>()
            changesPublisher.subscribe(bufferedChanges)
            Pair(stateMachines.keys.toList(), bufferedChanges)
        }
    }

    private fun restoreFibersFromCheckpoints() {
        mutex.locked {
            checkpointStorage.forEach {
                // If a flow is added before start() then don't attempt to restore it
                if (!stateMachines.containsValue(it)) {
                    val fiber = deserializeFiber(it.serializedFiber)
                    initFiber(fiber)
                    stateMachines[fiber] = it
                }
                true
            }
        }
    }

    private fun resumeRestoredFibers() {
        mutex.locked {
            started = true
            stateMachines.keys.forEach { resumeRestoredFiber(it) }
        }
        serviceHub.networkService.addMessageHandler(sessionTopic) { message, reg ->
            executor.checkOnThread()
            val sessionMessage = message.data.deserialize<SessionMessage>()
            when (sessionMessage) {
                is ExistingSessionMessage -> onExistingSessionMessage(sessionMessage)
                is SessionInit -> {
                    // TODO SECURITY Look up the party with the full X.500 name instead of just the legal name which
                    // isn't required to be unique
                    // TODO For now have the doorman block signups with identical names, and names with characters that
                    // are used in X.500 name textual serialisation
                    val otherParty = serviceHub.networkMapCache.getNodeByLegalName(message.peer.commonName)?.legalIdentity
                    if (otherParty != null) {
                        onSessionInit(sessionMessage, otherParty)
                    } else {
                        logger.error("Unknown peer ${message.peer} in $sessionMessage")
                    }
                }
            }
        }
    }

    private fun resumeRestoredFiber(fiber: FlowStateMachineImpl<*>) {
        fiber.openSessions.values.forEach { openSessions[it.ourSessionId] = it }
        if (fiber.openSessions.values.any { it.waitingForResponse }) {
            fiber.logger.info("Restored, pending on receive")
        } else {
            resumeFiber(fiber)
        }
    }

    private fun onExistingSessionMessage(message: ExistingSessionMessage) {
        val session = openSessions[message.recipientSessionId]
        if (session != null) {
            session.psm.logger.trace { "Received $message on $session" }
            if (message is SessionEnd) {
                openSessions.remove(message.recipientSessionId)
            }
            session.receivedMessages += message
            if (session.waitingForResponse) {
                // We only want to resume once, so immediately reset the flag.
                session.waitingForResponse = false
                updateCheckpoint(session.psm)
                resumeFiber(session.psm)
            }
        } else {
            val otherParty = recentlyClosedSessions.remove(message.recipientSessionId)
            if (otherParty != null) {
                if (message is SessionConfirm) {
                    logger.debug { "Received session confirmation but associated fiber has already terminated, so sending session end" }
                    sendSessionMessage(otherParty, SessionEnd(message.initiatedSessionId), null)
                } else {
                    logger.trace { "Ignoring session end message for already closed session: $message" }
                }
            } else {
                logger.warn("Received a session message for unknown session: $message")
            }
        }
    }

    private fun onSessionInit(sessionInit: SessionInit, otherParty: Party) {
        logger.trace { "Received $sessionInit $otherParty" }
        val otherPartySessionId = sessionInit.initiatorSessionId
        try {
            val markerClass = Class.forName(sessionInit.flowName)
            val flowFactory = serviceHub.getFlowFactory(markerClass)
            if (flowFactory != null) {
                val flow = flowFactory(otherParty)
                val psm = createFiber(flow)
                val session = FlowSession(flow, otherParty, random63BitValue(), otherPartySessionId)
                if (sessionInit.firstPayload != null) {
                    session.receivedMessages += SessionData(session.ourSessionId, sessionInit.firstPayload)
                }
                openSessions[session.ourSessionId] = session
                psm.openSessions[Pair(flow, otherParty)] = session
                updateCheckpoint(psm)
                sendSessionMessage(otherParty, SessionConfirm(otherPartySessionId, session.ourSessionId), psm)
                psm.logger.debug { "Initiated from $sessionInit on $session" }
                startFiber(psm)
            } else {
                logger.warn("Unknown flow marker class in $sessionInit")
                sendSessionMessage(otherParty, SessionReject(otherPartySessionId, "Don't know ${markerClass.name}"), null)
            }
        } catch (e: Exception) {
            logger.warn("Received invalid $sessionInit", e)
            sendSessionMessage(otherParty, SessionReject(otherPartySessionId, "Unable to establish session"), null)
        }
    }

    private fun serializeFiber(fiber: FlowStateMachineImpl<*>): SerializedBytes<FlowStateMachineImpl<*>> {
        val kryo = quasarKryo()
        // add the map of tokens -> tokenizedServices to the kyro context
        SerializeAsTokenSerializer.setContext(kryo, serializationContext)
        return fiber.serialize(kryo)
    }

    private fun deserializeFiber(serialisedFiber: SerializedBytes<FlowStateMachineImpl<*>>): FlowStateMachineImpl<*> {
        val kryo = quasarKryo()
        // put the map of token -> tokenized into the kryo context
        SerializeAsTokenSerializer.setContext(kryo, serializationContext)
        return serialisedFiber.deserialize(kryo).apply { fromCheckpoint = true }
    }

    private fun quasarKryo(): Kryo {
        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
        return createKryo(serializer.kryo)
    }

    private fun <T> createFiber(logic: FlowLogic<T>): FlowStateMachineImpl<T> {
        val id = StateMachineRunId.createRandom()
        return FlowStateMachineImpl(id, logic, scheduler).apply { initFiber(this) }
    }

    private fun initFiber(psm: FlowStateMachineImpl<*>) {
        psm.database = database
        psm.serviceHub = serviceHub
        psm.actionOnSuspend = { ioRequest ->
            updateCheckpoint(psm)
            // We commit on the fibers transaction that was copied across ThreadLocals during suspend
            // This will free up the ThreadLocal so on return the caller can carry on with other transactions
            psm.commitTransaction()
            processIORequest(ioRequest)
            decrementLiveFibers()
        }
        psm.actionOnEnd = {
            try {
                psm.logic.progressTracker?.currentStep = ProgressTracker.DONE
                mutex.locked {
                    stateMachines.remove(psm)?.let { checkpointStorage.removeCheckpoint(it) }
                    totalFinishedFlows.inc()
                    unfinishedFibers.countDown()
                    notifyChangeObservers(psm, AddOrRemove.REMOVE)
                }
                endAllFiberSessions(psm)
            } finally {
                decrementLiveFibers()
            }
        }
        mutex.locked {
            totalStartedFlows.inc()
            unfinishedFibers.countUp()
            notifyChangeObservers(psm, AddOrRemove.ADD)
        }
    }

    private fun endAllFiberSessions(psm: FlowStateMachineImpl<*>) {
        openSessions.values.removeIf { session ->
            if (session.psm == psm) {
                val otherPartySessionId = session.otherPartySessionId
                if (otherPartySessionId != null) {
                    sendSessionMessage(session.otherParty, SessionEnd(otherPartySessionId), psm)
                }
                recentlyClosedSessions[session.ourSessionId] = session.otherParty
                true
            } else {
                false
            }
        }
    }

    private fun startFiber(fiber: FlowStateMachineImpl<*>) {
        try {
            resumeFiber(fiber)
        } catch (e: ExecutionException) {
            // There are two ways we can take exceptions in this method:
            //
            // 1) A bug in the SMM code itself whilst setting up the new flow. In that case the exception will
            //    propagate out of this method as it would for any method.
            //
            // 2) An exception in the first part of the fiber after it's been invoked for the first time via
            //    fiber.start(). In this case the exception will be caught and stashed in the flow logic future,
            //    then sent to the unhandled exception handler above which logs it, and is then rethrown wrapped
            //    in an ExecutionException or RuntimeException+EE so we can just catch it here and ignore it.
        } catch (e: RuntimeException) {
            if (e.cause !is ExecutionException)
                throw e
        }
    }

    /**
     * Kicks off a brand new state machine of the given class.
     * The state machine will be persisted when it suspends, with automated restart if the StateMachineManager is
     * restarted with checkpointed state machines in the storage service.
     */
    fun <T> add(logic: FlowLogic<T>): FlowStateMachine<T> {
        // We swap out the parent transaction context as using this frequently leads to a deadlock as we wait
        // on the flow completion future inside that context. The problem is that any progress checkpoints are
        // unable to acquire the table lock and move forward till the calling transaction finishes.
        // Committing in line here on a fresh context ensure we can progress.
        val fiber = isolatedTransaction(database) {
            val fiber = createFiber(logic)
            updateCheckpoint(fiber)
            fiber
        }
        // If we are not started then our checkpoint will be picked up during start
        mutex.locked {
            if (started) {
                startFiber(fiber)
            }
        }
        return fiber
    }

    private fun updateCheckpoint(psm: FlowStateMachineImpl<*>) {
        check(psm.state != Strand.State.RUNNING) { "Fiber cannot be running when checkpointing" }
        val newCheckpoint = Checkpoint(serializeFiber(psm))
        val previousCheckpoint = mutex.locked { stateMachines.put(psm, newCheckpoint) }
        if (previousCheckpoint != null) {
            checkpointStorage.removeCheckpoint(previousCheckpoint)
        }
        checkpointStorage.addCheckpoint(newCheckpoint)
        checkpointingMeter.mark()
    }

    private fun resumeFiber(psm: FlowStateMachineImpl<*>) {
        // Avoid race condition when setting stopping to true and then checking liveFibers
        incrementLiveFibers()
        if (!stopping) executor.executeASAP {
            psm.resume(scheduler)
        } else {
            psm.logger.debug("Not resuming as SMM is stopping.")
            decrementLiveFibers()
        }
    }

    private fun processIORequest(ioRequest: FlowIORequest) {
        if (ioRequest is SendRequest) {
            if (ioRequest.message is SessionInit) {
                openSessions[ioRequest.session.ourSessionId] = ioRequest.session
            }
            sendSessionMessage(ioRequest.session.otherParty, ioRequest.message, ioRequest.session.psm)
            if (ioRequest !is ReceiveRequest<*>) {
                // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
                resumeFiber(ioRequest.session.psm)
            }
        }
    }

    private fun sendSessionMessage(party: Party, message: SessionMessage, psm: FlowStateMachineImpl<*>?) {
        val node = serviceHub.networkMapCache.getNodeByCompositeKey(party.owningKey)
                ?: throw IllegalArgumentException("Don't know about party $party")
        val logger = psm?.logger ?: logger
        logger.trace { "Sending $message to party $party" }
        serviceHub.networkService.send(sessionTopic, message, node.address)
    }


    interface SessionMessage

    interface ExistingSessionMessage : SessionMessage {
        val recipientSessionId: Long
    }

    data class SessionInit(val initiatorSessionId: Long, val flowName: String, val firstPayload: Any?) : SessionMessage

    interface SessionInitResponse : ExistingSessionMessage

    data class SessionConfirm(val initiatorSessionId: Long, val initiatedSessionId: Long) : SessionInitResponse {
        override val recipientSessionId: Long get() = initiatorSessionId
    }

    data class SessionReject(val initiatorSessionId: Long, val errorMessage: String) : SessionInitResponse {
        override val recipientSessionId: Long get() = initiatorSessionId
    }

    data class SessionData(override val recipientSessionId: Long, val payload: Any) : ExistingSessionMessage {
        override fun toString(): String {
            return "${javaClass.simpleName}(recipientSessionId=$recipientSessionId, payload=${payload.toString().abbreviate(100)})"
        }
    }

    data class SessionEnd(override val recipientSessionId: Long) : ExistingSessionMessage


    data class FlowSession(val flow: FlowLogic<*>,
                           val otherParty: Party,
                           val ourSessionId: Long,
                           var otherPartySessionId: Long?,
                           @Volatile var waitingForResponse: Boolean = false) {

        val receivedMessages = ConcurrentLinkedQueue<ExistingSessionMessage>()
        val psm: FlowStateMachineImpl<*> get() = flow.fsm as FlowStateMachineImpl<*>

    }

}
