package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import co.paralleluniverse.strands.Strand
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.ListenableFuture
import io.requery.util.CloseableIterator
import net.corda.core.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.internal.SessionRejectException
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.node.services.messaging.TopicSession
import net.corda.node.utilities.*
import org.apache.activemq.artemis.utils.ReusableLatch
import org.jetbrains.exposed.sql.Database
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.ArrayList

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
 * TODO: Don't store all active flows in memory, load from the database on demand.
 */
@ThreadSafe
class StateMachineManager(val serviceHub: ServiceHubInternal,
                          val checkpointStorage: CheckpointStorage,
                          val executor: AffinityExecutor,
                          val database: Database,
                          private val unfinishedFibers: ReusableLatch = ReusableLatch()) {

    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    private val quasarKryoPool = KryoPool.Builder {
        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
        DefaultKryoCustomizer.customize(serializer.kryo)
        serializer.kryo.addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
        serializer.kryo
    }.build()

    private object AutoCloseableSerialisationDetector : Serializer<AutoCloseable>() {
        override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
            val message = if (closeable is CloseableIterator<*>) {
                "A live Iterator pointing to the database has been detected during flow checkpointing. This may be due " +
                        "to a Vault query - move it into a private method."
            } else {
                "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow checkpointing. " +
                        "Restoring such resources across node restarts is not supported. Make sure code accessing it is " +
                        "confined to a private method or the reference is nulled out."
            }
            throw UnsupportedOperationException(message)
        }

        override fun read(kryo: Kryo, input: Input, type: Class<AutoCloseable>) = throw IllegalStateException("Should not reach here!")
    }

    companion object {
        private val logger = loggerFor<StateMachineManager>()
        internal val sessionTopic = TopicSession("platform.session")

        init {
            Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
                (fiber as FlowStateMachineImpl<*>).logger.warn("Caught exception from flow", throwable)
            }
        }
    }

    val scheduler = FiberScheduler()

    sealed class Change {
        abstract val logic: FlowLogic<*>

        data class Add(override val logic: FlowLogic<*>) : Change()
        data class Removed(override val logic: FlowLogic<*>, val result: ErrorOr<*>) : Change()
    }

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private class InnerState {
        var started = false
        val stateMachines = LinkedHashMap<FlowStateMachineImpl<*>, Checkpoint>()
        val changesPublisher = PublishSubject.create<Change>()!!
        val fibersWaitingForLedgerCommit = HashMultimap.create<SecureHash, FlowStateMachineImpl<*>>()!!

        fun notifyChangeObservers(change: Change) {
            changesPublisher.bufferUntilDatabaseCommit().onNext(change)
        }
    }

    private val mutex = ThreadBox(InnerState())

    // True if we're shutting down, so don't resume anything.
    @Volatile private var stopping = false
    // How many Fibers are running and not suspended.  If zero and stopping is true, then we are halted.
    private val liveFibers = ReusableLatch()


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

    internal val tokenizableServices = ArrayList<Any>()
    // Context for tokenized services in checkpoints
    private val serializationContext by lazy {
        SerializeAsTokenContext(tokenizableServices, quasarKryoPool, serviceHub)
    }

    /** Returns a list of all state machines executing the given flow logic at the top level (subflows do not count) */
    fun <P : FlowLogic<T>, T> findStateMachines(flowClass: Class<P>): List<Pair<P, ListenableFuture<T>>> {
        @Suppress("UNCHECKED_CAST")
        return mutex.locked {
            stateMachines.keys
                    .map { it.logic }
                    .filterIsInstance(flowClass)
                    .map { it to (it.stateMachine as FlowStateMachineImpl<T>).resultFuture }
        }
    }

    val allStateMachines: List<FlowLogic<*>>
        get() = mutex.locked { stateMachines.keys.map { it.logic } }

    /**
     * An observable that emits triples of the changing flow, the type of change, and a process-specific ID number
     * which may change across restarts.
     *
     * We use assignment here so that multiple subscribers share the same wrapped Observable.
     */
    val changes: Observable<Change> = mutex.content.changesPublisher.wrapWithDatabaseTransaction()

    fun start() {
        restoreFibersFromCheckpoints()
        listenToLedgerTransactions()
        serviceHub.networkMapCache.mapServiceRegistered.then(executor) { resumeRestoredFibers() }
    }

    private fun listenToLedgerTransactions() {
        // Observe the stream of committed, validated transactions and resume fibers that are waiting for them.
        serviceHub.validatedTransactions.updates.subscribe { stx ->
            val hash = stx.id
            val fibers: Set<FlowStateMachineImpl<*>> = mutex.locked { fibersWaitingForLedgerCommit.removeAll(hash) }
            if (fibers.isNotEmpty()) {
                executor.executeASAP {
                    for (fiber in fibers) {
                        fiber.logger.trace { "Transaction $hash has committed to the ledger, resuming" }
                        fiber.waitingForResponse = null
                        resumeFiber(fiber)
                    }
                }
            }
        }
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
    fun track(): DataFeed<List<FlowStateMachineImpl<*>>, Change> {
        return mutex.locked {
            DataFeed(stateMachines.keys.toList(), changesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    private fun restoreFibersFromCheckpoints() {
        mutex.locked {
            checkpointStorage.forEach {
                // If a flow is added before start() then don't attempt to restore it
                if (!stateMachines.containsValue(it)) {
                    val fiber = deserializeFiber(it)
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
        serviceHub.networkService.addMessageHandler(sessionTopic) { message, _ ->
            executor.checkOnThread()
            onSessionMessage(message)
        }
    }

    private fun resumeRestoredFiber(fiber: FlowStateMachineImpl<*>) {
        fiber.openSessions.values.forEach { openSessions[it.ourSessionId] = it }
        val waitingForResponse = fiber.waitingForResponse
        if (waitingForResponse != null) {
            if (waitingForResponse is WaitForLedgerCommit) {
                val stx = database.transaction {
                    serviceHub.validatedTransactions.getTransaction(waitingForResponse.hash)
                }
                if (stx != null) {
                    fiber.logger.info("Resuming fiber as tx ${waitingForResponse.hash} has committed")
                    fiber.waitingForResponse = null
                    resumeFiber(fiber)
                } else {
                    fiber.logger.info("Restored, pending on ledger commit of ${waitingForResponse.hash}")
                    mutex.locked { fibersWaitingForLedgerCommit.put(waitingForResponse.hash, fiber) }
                }
            } else {
                fiber.logger.info("Restored, pending on receive")
            }
        } else {
            resumeFiber(fiber)
        }
    }

    private fun onSessionMessage(message: ReceivedMessage) {
        val sessionMessage = message.data.deserialize<SessionMessage>()
        val sender = serviceHub.networkMapCache.getNodeByLegalName(message.peer)?.legalIdentity
        if (sender != null) {
            when (sessionMessage) {
                is ExistingSessionMessage -> onExistingSessionMessage(sessionMessage, sender)
                is SessionInit -> onSessionInit(sessionMessage, message, sender)
            }
        } else {
            logger.error("Unknown peer ${message.peer} in $sessionMessage")
        }
    }

    private fun onExistingSessionMessage(message: ExistingSessionMessage, sender: Party) {
        val session = openSessions[message.recipientSessionId]
        if (session != null) {
            session.fiber.logger.trace { "Received $message on $session from $sender" }
            if (session.retryable) {
                if (message is SessionConfirm && session.state is FlowSessionState.Initiated) {
                    session.fiber.logger.trace { "Ignoring duplicate confirmation for session ${session.ourSessionId} – session is idempotent" }
                    return
                }
                if (message !is SessionConfirm) {
                    serviceHub.networkService.cancelRedelivery(session.ourSessionId)
                }
            }
            if (message is SessionEnd) {
                openSessions.remove(message.recipientSessionId)
            }
            session.receivedMessages += ReceivedSessionMessage(sender, message)
            if (resumeOnMessage(message, session)) {
                // It's important that we reset here and not after the fiber's resumed, in case we receive another message
                // before then.
                session.fiber.waitingForResponse = null
                updateCheckpoint(session.fiber)
                session.fiber.logger.trace { "Resuming due to $message" }
                resumeFiber(session.fiber)
            }
        } else {
            val peerParty = recentlyClosedSessions.remove(message.recipientSessionId)
            if (peerParty != null) {
                if (message is SessionConfirm) {
                    logger.trace { "Received session confirmation but associated fiber has already terminated, so sending session end" }
                    sendSessionMessage(peerParty, NormalSessionEnd(message.initiatedSessionId))
                } else {
                    logger.trace { "Ignoring session end message for already closed session: $message" }
                }
            } else {
                logger.warn("Received a session message for unknown session: $message, from $sender")
            }
        }
    }

    // We resume the fiber if it's received a response for which it was waiting for or it's waiting for a ledger
    // commit but a counterparty flow has ended with an error (in which case our flow also has to end)
    private fun resumeOnMessage(message: ExistingSessionMessage, session: FlowSession): Boolean {
        val waitingForResponse = session.fiber.waitingForResponse
        return (waitingForResponse as? ReceiveRequest<*>)?.session === session ||
                waitingForResponse is WaitForLedgerCommit && message is ErrorSessionEnd
    }

    private fun onSessionInit(sessionInit: SessionInit, receivedMessage: ReceivedMessage, sender: Party) {
        logger.trace { "Received $sessionInit from $sender" }
        val otherPartySessionId = sessionInit.initiatorSessionId

        fun sendSessionReject(message: String) = sendSessionMessage(sender, SessionReject(otherPartySessionId, message))

        val initiatedFlowFactory = serviceHub.getFlowFactory(sessionInit.initiatingFlowClass)
        if (initiatedFlowFactory == null) {
            logger.warn("${sessionInit.initiatingFlowClass} has not been registered: $sessionInit")
            sendSessionReject("${sessionInit.initiatingFlowClass.name} has not been registered")
            return
        }

        val session = try {
            val flow = initiatedFlowFactory.createFlow(receivedMessage.platformVersion, sender, sessionInit)
            val fiber = createFiber(flow, FlowInitiator.Peer(sender))
            val session = FlowSession(flow, random63BitValue(), sender, FlowSessionState.Initiated(sender, otherPartySessionId))
            if (sessionInit.firstPayload != null) {
                session.receivedMessages += ReceivedSessionMessage(sender, SessionData(session.ourSessionId, sessionInit.firstPayload))
            }
            openSessions[session.ourSessionId] = session
            fiber.openSessions[Pair(flow, sender)] = session
            updateCheckpoint(fiber)
            session
        } catch (e: SessionRejectException) {
            logger.warn("${e.logMessage}: $sessionInit")
            sendSessionReject(e.rejectMessage)
            return
        } catch (e: Exception) {
            logger.warn("Couldn't start flow session from $sessionInit", e)
            sendSessionReject("Unable to establish session")
            return
        }

        sendSessionMessage(sender, SessionConfirm(otherPartySessionId, session.ourSessionId), session.fiber)
        session.fiber.logger.debug { "Initiated by $sender using ${sessionInit.initiatingFlowClass.name}" }
        session.fiber.logger.trace { "Initiated from $sessionInit on $session" }
        resumeFiber(session.fiber)
    }

    private fun serializeFiber(fiber: FlowStateMachineImpl<*>): SerializedBytes<FlowStateMachineImpl<*>> {
        return quasarKryoPool.run { kryo ->
            // add the map of tokens -> tokenizedServices to the kyro context
            kryo.withSerializationContext(serializationContext) {
                fiber.serialize(kryo)
            }
        }
    }

    private fun deserializeFiber(checkpoint: Checkpoint): FlowStateMachineImpl<*> {
        return quasarKryoPool.run { kryo ->
            // put the map of token -> tokenized into the kryo context
            kryo.withSerializationContext(serializationContext) {
                checkpoint.serializedFiber.deserialize(kryo)
            }.apply { fromCheckpoint = true }
        }
    }

    private fun <T> createFiber(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachineImpl<T> {
        val id = StateMachineRunId.createRandom()
        return FlowStateMachineImpl(id, logic, scheduler, flowInitiator).apply { initFiber(this) }
    }

    private fun initFiber(fiber: FlowStateMachineImpl<*>) {
        fiber.database = database
        fiber.serviceHub = serviceHub
        fiber.actionOnSuspend = { ioRequest ->
            updateCheckpoint(fiber)
            // We commit on the fibers transaction that was copied across ThreadLocals during suspend
            // This will free up the ThreadLocal so on return the caller can carry on with other transactions
            fiber.commitTransaction()
            processIORequest(ioRequest)
            decrementLiveFibers()
        }
        fiber.actionOnEnd = { resultOrError, propagated ->
            try {
                mutex.locked {
                    stateMachines.remove(fiber)?.let { checkpointStorage.removeCheckpoint(it) }
                    notifyChangeObservers(Change.Removed(fiber.logic, resultOrError))
                }
                endAllFiberSessions(fiber, resultOrError.error, propagated)
            } finally {
                fiber.commitTransaction()
                decrementLiveFibers()
                totalFinishedFlows.inc()
                unfinishedFibers.countDown()
            }
        }
        mutex.locked {
            totalStartedFlows.inc()
            unfinishedFibers.countUp()
            notifyChangeObservers(Change.Add(fiber.logic))
        }
    }

    private fun endAllFiberSessions(fiber: FlowStateMachineImpl<*>, exception: Throwable?, propagated: Boolean) {
        openSessions.values.removeIf { session ->
            if (session.fiber == fiber) {
                session.endSession(exception, propagated)
                true
            } else {
                false
            }
        }
    }

    private fun FlowSession.endSession(exception: Throwable?, propagated: Boolean) {
        val initiatedState = state as? FlowSessionState.Initiated ?: return
        val sessionEnd = if (exception == null) {
            NormalSessionEnd(initiatedState.peerSessionId)
        } else {
            val errorResponse = if (exception is FlowException && (!propagated || initiatingParty != null)) {
                // Only propagate this FlowException if our local flow threw it or it was propagated to us and we only
                // pass it down invocation chain to the flow that initiated us, not to flows we've started sessions with.
                exception
            } else {
                null
            }
            ErrorSessionEnd(initiatedState.peerSessionId, errorResponse)
        }
        sendSessionMessage(initiatedState.peerParty, sessionEnd, fiber)
        recentlyClosedSessions[ourSessionId] = initiatedState.peerParty
    }

    /**
     * Kicks off a brand new state machine of the given class.
     * The state machine will be persisted when it suspends, with automated restart if the StateMachineManager is
     * restarted with checkpointed state machines in the storage service.
     *
     * Note that you must be on the [executor] thread.
     */
    fun <T> add(logic: FlowLogic<T>, flowInitiator: FlowInitiator): FlowStateMachineImpl<T> {
        // TODO: Check that logic has @Suspendable on its call method.
        executor.checkOnThread()
        // We swap out the parent transaction context as using this frequently leads to a deadlock as we wait
        // on the flow completion future inside that context. The problem is that any progress checkpoints are
        // unable to acquire the table lock and move forward till the calling transaction finishes.
        // Committing in line here on a fresh context ensure we can progress.
        val fiber = database.isolatedTransaction {
            val fiber = createFiber(logic, flowInitiator)
            updateCheckpoint(fiber)
            fiber
        }
        // If we are not started then our checkpoint will be picked up during start
        mutex.locked {
            if (started) {
                resumeFiber(fiber)
            }
        }
        return fiber
    }

    private fun updateCheckpoint(fiber: FlowStateMachineImpl<*>) {
        check(fiber.state != Strand.State.RUNNING) { "Fiber cannot be running when checkpointing" }
        val newCheckpoint = Checkpoint(serializeFiber(fiber))
        val previousCheckpoint = mutex.locked { stateMachines.put(fiber, newCheckpoint) }
        if (previousCheckpoint != null) {
            checkpointStorage.removeCheckpoint(previousCheckpoint)
        }
        checkpointStorage.addCheckpoint(newCheckpoint)
        checkpointingMeter.mark()
    }

    private fun resumeFiber(fiber: FlowStateMachineImpl<*>) {
        // Avoid race condition when setting stopping to true and then checking liveFibers
        incrementLiveFibers()
        if (!stopping) {
            executor.executeASAP {
                fiber.resume(scheduler)
            }
        } else {
            fiber.logger.trace("Not resuming as SMM is stopping.")
            decrementLiveFibers()
        }
    }

    private fun processIORequest(ioRequest: FlowIORequest) {
        executor.checkOnThread()
        when (ioRequest) {
            is SendRequest -> processSendRequest(ioRequest)
            is WaitForLedgerCommit -> processWaitForCommitRequest(ioRequest)
        }
    }

    private fun processSendRequest(ioRequest: SendRequest) {
        val retryId = if (ioRequest.message is SessionInit) {
            with(ioRequest.session) {
                openSessions[ourSessionId] = this
                if (retryable) ourSessionId else null
            }
        } else null
        sendSessionMessage(ioRequest.session.state.sendToParty, ioRequest.message, ioRequest.session.fiber, retryId)
        if (ioRequest !is ReceiveRequest<*>) {
            // We sent a message, but don't expect a response, so re-enter the continuation to let it keep going.
            resumeFiber(ioRequest.session.fiber)
        }
    }

    private fun processWaitForCommitRequest(ioRequest: WaitForLedgerCommit) {
        // Is it already committed?
        val stx = database.transaction {
            serviceHub.validatedTransactions.getTransaction(ioRequest.hash)
        }
        if (stx != null) {
            resumeFiber(ioRequest.fiber)
        } else {
            // No, then register to wait.
            //
            // We assume this code runs on the server thread, which is the only place transactions are committed
            // currently. When we liberalise our threading somewhat, handing of wait requests will need to be
            // reworked to make the wait atomic in another way. Otherwise there is a race between checking the
            // database and updating the waiting list.
            mutex.locked {
                fibersWaitingForLedgerCommit[ioRequest.hash] += ioRequest.fiber
            }
        }
    }

    private fun sendSessionMessage(party: Party, message: SessionMessage, fiber: FlowStateMachineImpl<*>? = null, retryId: Long? = null) {
        val partyInfo = serviceHub.networkMapCache.getPartyInfo(party)
                ?: throw IllegalArgumentException("Don't know about party $party")
        val address = serviceHub.networkService.getAddressOfParty(partyInfo)
        val logger = fiber?.logger ?: logger
        logger.trace { "Sending $message to party $party @ $address" + if (retryId != null) " with retry $retryId" else "" }

        val serialized = try {
            message.serialize()
        } catch (e: KryoException) {
            if (message !is ErrorSessionEnd || message.errorResponse == null) throw e
            logger.warn("Something in ${message.errorResponse.javaClass.name} is not serialisable. " +
                    "Instead sending back an exception which is serialisable to ensure session end occurs properly.", e)
            // The subclass may have overridden toString so we use that
            val exMessage = message.errorResponse.let { if (it.javaClass != FlowException::class.java) it.toString() else it.message }
            message.copy(errorResponse = FlowException(exMessage)).serialize()
        }

        serviceHub.networkService.apply {
            send(createMessage(sessionTopic, serialized.bytes), address, retryId = retryId)
        }
    }
}
