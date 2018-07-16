package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.fibers.instrument.SuspendableHelper
import co.paralleluniverse.strands.Strand
import com.codahale.metrics.Gauge
import com.esotericsoftware.kryo.KryoException
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.MoreExecutors
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.SerializationDefaults.CHECKPOINT_CONTEXT
import net.corda.core.serialization.SerializationDefaults.SERIALIZATION_FACTORY
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.api.Checkpoint
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.shouldCheckCheckpoints
import net.corda.node.services.messaging.P2PMessagingHeaders
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.newNamedSingleThreadExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import net.corda.nodeapi.internal.serialization.SerializeAsTokenContextImpl
import net.corda.nodeapi.internal.serialization.withTokenContext
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import rx.Observable
import rx.subjects.PublishSubject
import java.io.NotSerializableException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.SECONDS
import javax.annotation.concurrent.ThreadSafe

/**
 * The StateMachineManagerImpl will always invoke the flow fibers on the given [AffinityExecutor], regardless of which
 * thread actually starts them via [startFlow].
 */
@ThreadSafe
class StateMachineManagerImpl(
        val serviceHub: ServiceHubInternal,
        val checkpointStorage: CheckpointStorage,
        val executor: AffinityExecutor,
        val database: CordaPersistence,
        private val unfinishedFibers: ReusableLatch = ReusableLatch(),
        private val classloader: ClassLoader = StateMachineManagerImpl::class.java.classLoader
) : StateMachineManager {
    inner class FiberScheduler : FiberExecutorScheduler("Same thread scheduler", executor)

    companion object {
        private val logger = contextLogger()
        internal val sessionTopic = "platform.session"

        init {
            Fiber.setDefaultUncaughtExceptionHandler { fiber, throwable ->
                (fiber as FlowStateMachineImpl<*>).logger.warn("Caught exception from flow", throwable)
            }
        }
    }

    // A list of all the state machines being managed by this class. We expose snapshots of it via the stateMachines
    // property.
    private class InnerState {
        var started = false
        val stateMachines = LinkedHashMap<FlowStateMachineImpl<*>, Checkpoint>()
        val changesPublisher = PublishSubject.create<StateMachineManager.Change>()!!
        val fibersWaitingForLedgerCommit = HashMultimap.create<SecureHash, FlowStateMachineImpl<*>>()!!

        fun notifyChangeObservers(change: StateMachineManager.Change) {
            changesPublisher.bufferUntilDatabaseCommit().onNext(change)
        }
    }

    private val scheduler = FiberScheduler()
    private val mutex = ThreadBox(InnerState())
    // This thread (only enabled in dev mode) deserialises checkpoints in the background to shake out bugs in checkpoint restore.
    private val checkpointCheckerThread = if (serviceHub.configuration.shouldCheckCheckpoints()) {
        newNamedSingleThreadExecutor("CheckpointChecker")
    } else {
        null
    }

    @Volatile private var unrestorableCheckpoints = false

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

    private val openSessions = ConcurrentHashMap<SessionId, FlowSessionInternal>()
    private val recentlyClosedSessions = ConcurrentHashMap<SessionId, Party>()

    // Context for tokenized services in checkpoints
    private lateinit var tokenizableServices: List<Any>
    private val serializationContext by lazy {
        SerializeAsTokenContextImpl(tokenizableServices, SERIALIZATION_FACTORY, CHECKPOINT_CONTEXT, serviceHub)
    }

    /** Returns a list of all state machines executing the given flow logic at the top level (subflows do not count) */
    override fun <A : FlowLogic<*>> findStateMachines(flowClass: Class<A>): List<Pair<A, CordaFuture<*>>> {
        return mutex.locked {
            stateMachines.keys.mapNotNull {
                flowClass.castIfPossible(it.logic)?.let { it to uncheckedCast<FlowStateMachine<*>, FlowStateMachineImpl<*>>(it.stateMachine).resultFuture }
            }
        }
    }

    override val allStateMachines: List<FlowLogic<*>>
        get() = mutex.locked { stateMachines.keys.map { it.logic } }

    /**
     * An observable that emits triples of the changing flow, the type of change, and a process-specific ID number
     * which may change across restarts.
     *
     * We use assignment here so that multiple subscribers share the same wrapped Observable.
     */
    override val changes: Observable<StateMachineManager.Change> = mutex.content.changesPublisher.wrapWithDatabaseTransaction()

    override fun start(tokenizableServices: List<Any>) {
        this.tokenizableServices = tokenizableServices
        checkQuasarJavaAgentPresence()
        restoreFibersFromCheckpoints()
        listenToLedgerTransactions()
        serviceHub.networkMapCache.nodeReady.then { executor.execute(this::resumeRestoredFibers) }
    }

    private fun checkQuasarJavaAgentPresence() {
        check(SuspendableHelper.isJavaAgentActive(), {
            """Missing the '-javaagent' JVM argument. Make sure you run the tests with the Quasar java agent attached to your JVM.
               #See https://docs.corda.net/troubleshooting.html - 'Fiber classes not instrumented' for more details.""".trimMargin("#")
        })
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
     * Start the shutdown process, bringing the [StateMachineManagerImpl] to a controlled stop.  When this method returns,
     * all Fibers have been suspended and checkpointed, or have completed.
     *
     * @param allowedUnsuspendedFiberCount Optional parameter is used in some tests.
     */
    override fun stop(allowedUnsuspendedFiberCount: Int) {
        require(allowedUnsuspendedFiberCount >= 0)
        mutex.locked {
            if (stopping) throw IllegalStateException("Already stopping!")
            stopping = true
        }
        // Account for any expected Fibers in a test scenario.
        liveFibers.countDown(allowedUnsuspendedFiberCount)
        liveFibers.await()
        checkpointCheckerThread?.let { MoreExecutors.shutdownAndAwaitTermination(it, 5, SECONDS) }
        check(!unrestorableCheckpoints) { "Unrestorable checkpoints where created, please check the logs for details." }
    }

    /**
     * Atomic get snapshot + subscribe. This is needed so we don't miss updates between subscriptions to [changes] and
     * calls to [allStateMachines]
     */
    override fun track(): DataFeed<List<FlowLogic<*>>, StateMachineManager.Change> {
        return mutex.locked {
            DataFeed(stateMachines.keys.map { it.logic }, changesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    private fun restoreFibersFromCheckpoints() {
        mutex.locked {
            checkpointStorage.forEach { checkpoint ->
                // If a flow is added before start() then don't attempt to restore it
                if (!stateMachines.containsValue(checkpoint)) {
                    deserializeFiber(checkpoint, logger)?.let {
                        initFiber(it)
                        stateMachines[it] = checkpoint
                    }
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
        val peer = message.peer
        val sessionMessage = try {
            message.data.deserialize<SessionMessage>()
        } catch (ex: Exception) {
            logger.error("Received corrupt SessionMessage data from $peer")
            return
        }
        val sender = serviceHub.networkMapCache.getPeerByLegalName(peer)
        if (sender != null) {
            when (sessionMessage) {
                is ExistingSessionMessage -> onExistingSessionMessage(sessionMessage, sender)
                is InitialSessionMessage -> onSessionInit(sessionMessage, message, sender)
            }
        } else {
            logger.error("Unknown peer $peer in $sessionMessage")
        }
    }

    private fun onExistingSessionMessage(message: ExistingSessionMessage, sender: Party) {
        val session = openSessions[message.recipientSessionId]
        if (session != null) {
            session.fiber.pushToLoggingContext()
            session.fiber.logger.trace { "Received $message on $session from $sender" }
            if (session.retryable) {
                if (message.payload is ConfirmSessionMessage && session.state is FlowSessionState.Initiated) {
                    session.fiber.logger.trace { "Ignoring duplicate confirmation for session ${session.ourSessionId} – session is idempotent" }
                    return
                }
                if (message.payload !is ConfirmSessionMessage) {
                    serviceHub.networkService.cancelRedelivery(session.ourSessionId.toLong)
                }
            }
            if (message.payload is EndSessionMessage || message.payload is ErrorSessionMessage) {
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
                if (message.payload is ConfirmSessionMessage) {
                    logger.trace { "Received session confirmation but associated fiber has already terminated, so sending session end" }
                    sendSessionMessage(peerParty, ExistingSessionMessage(message.payload.initiatedSessionId, EndSessionMessage))
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
    private fun resumeOnMessage(message: ExistingSessionMessage, session: FlowSessionInternal): Boolean {
        val waitingForResponse = session.fiber.waitingForResponse
        return waitingForResponse?.shouldResume(message, session) ?: false
    }

    private fun onSessionInit(sessionInit: InitialSessionMessage, receivedMessage: ReceivedMessage, sender: Party) {

        logger.trace { "Received $sessionInit from $sender" }
        val senderSessionId = sessionInit.initiatorSessionId

        fun sendSessionReject(message: String) = sendSessionMessage(sender, ExistingSessionMessage(senderSessionId, RejectSessionMessage(message, errorId = sessionInit.initiatorSessionId.toLong)))

        val (session, initiatedFlowFactory) = try {
            val initiatedFlowFactory = getInitiatedFlowFactory(sessionInit)
            val flowSession = FlowSessionImpl(sender)
            val flow = initiatedFlowFactory.createFlow(flowSession)
            val senderFlowVersion = when (initiatedFlowFactory) {
                is InitiatedFlowFactory.Core -> receivedMessage.platformVersion  // The flow version for the core flows is the platform version
                is InitiatedFlowFactory.CorDapp -> sessionInit.flowVersion
            }
            val session = FlowSessionInternal(
                    flow,
                    flowSession,
                    SessionId.createRandom(newSecureRandom()),
                    sender,
                    FlowSessionState.Initiated(sender, senderSessionId, FlowInfo(senderFlowVersion, sessionInit.appName)))
            if (sessionInit.firstPayload != null) {
                session.receivedMessages += ReceivedSessionMessage(sender, ExistingSessionMessage(session.ourSessionId, DataSessionMessage(sessionInit.firstPayload)))
            }
            openSessions[session.ourSessionId] = session
            val context = InvocationContext.peer(sender.name)
            val fiber = createFiber(flow, context)
            fiber.pushToLoggingContext()
            logger.info("Accepting flow session from party ${sender.name}. Session id for tracing purposes is ${sessionInit.initiatorSessionId}.")
            flowSession.sessionFlow = flow
            flowSession.stateMachine = fiber
            fiber.openSessions[Pair(flow, sender)] = session
            updateCheckpoint(fiber)
            session to initiatedFlowFactory
        } catch (e: SessionRejectException) {
            logger.warn("${e.logMessage}: $sessionInit")
            sendSessionReject(e.rejectMessage)
            return
        } catch (e: Exception) {
            logger.warn("Couldn't start flow session from $sessionInit", e)
            sendSessionReject("Unable to establish session")
            return
        }

        val (ourFlowVersion, appName) = when (initiatedFlowFactory) {
        // The flow version for the core flows is the platform version
            is InitiatedFlowFactory.Core -> serviceHub.myInfo.platformVersion to "corda"
            is InitiatedFlowFactory.CorDapp -> initiatedFlowFactory.flowVersion to initiatedFlowFactory.appName
        }

        sendSessionMessage(sender, ExistingSessionMessage(senderSessionId, ConfirmSessionMessage(session.ourSessionId, FlowInfo(ourFlowVersion, appName))), session.fiber)
        session.fiber.logger.debug { "Initiated by $sender using ${sessionInit.initiatorFlowClassName}" }
        session.fiber.logger.trace { "Initiated from $sessionInit on $session" }
        resumeFiber(session.fiber)
    }

    private fun getInitiatedFlowFactory(sessionInit: InitialSessionMessage): InitiatedFlowFactory<*> {
        val initiatingFlowClass = try {
            Class.forName(sessionInit.initiatorFlowClassName, true, classloader).asSubclass(FlowLogic::class.java)
        } catch (e: ClassNotFoundException) {
            throw SessionRejectException("Don't know ${sessionInit.initiatorFlowClassName}")
        } catch (e: ClassCastException) {
            throw SessionRejectException("${sessionInit.initiatorFlowClassName} is not a flow")
        }
        return serviceHub.getFlowFactory(initiatingFlowClass) ?:
                throw SessionRejectException("$initiatingFlowClass is not registered")
    }

    private fun serializeFiber(fiber: FlowStateMachineImpl<*>): SerializedBytes<FlowStateMachineImpl<*>> {
        return fiber.serialize(context = CHECKPOINT_CONTEXT.withTokenContext(serializationContext))
    }

    private fun deserializeFiber(checkpoint: Checkpoint, logger: Logger): FlowStateMachineImpl<*>? {
        return try {
            checkpoint.serializedFiber.deserialize(context = CHECKPOINT_CONTEXT.withTokenContext(serializationContext)).apply {
                fromCheckpoint = true
            }
        } catch (t: Throwable) {
            logger.error("Encountered unrestorable checkpoint!", t)
            null
        }
    }

    private fun <T> createFiber(logic: FlowLogic<T>, context: InvocationContext, ourIdentity: Party? = null): FlowStateMachineImpl<T> {
        val fsm = FlowStateMachineImpl(
                StateMachineRunId.createRandom(),
                logic,
                scheduler,
                ourIdentity ?: serviceHub.myInfo.legalIdentities[0],
                context)
        initFiber(fsm)
        return fsm
    }

    private fun initFiber(fiber: FlowStateMachineImpl<*>) {
        verifyFlowLogicIsSuspendable(fiber.logic)
        fiber.database = database
        fiber.serviceHub = serviceHub
        fiber.ourIdentityAndCert = serviceHub.myInfo.legalIdentitiesAndCerts.find { it.party == fiber.ourIdentity }
                ?: throw IllegalStateException("Identity specified by ${fiber.id} (${fiber.ourIdentity.name}) is not one of ours!")
        fiber.actionOnSuspend = { ioRequest ->
            updateCheckpoint(fiber)
            // We commit on the fibers transaction that was copied across ThreadLocals during suspend
            // This will free up the ThreadLocal so on return the caller can carry on with other transactions
            fiber.commitTransaction()
            processIORequest(ioRequest)
            decrementLiveFibers()
        }
        fiber.actionOnEnd = { result, propagated ->
            try {
                mutex.locked {
                    stateMachines.remove(fiber)?.let { checkpointStorage.removeCheckpoint(it) }
                    notifyChangeObservers(StateMachineManager.Change.Removed(fiber.logic, result))
                }
                endAllFiberSessions(fiber, result, propagated)
            } finally {
                if (result.isSuccess) {
                    fiber.commitTransaction()
                    totalFinishedFlows.inc()
                    unfinishedFibers.countDown()
                } else {
                    fiber.rollbackTransaction()
                }
                decrementLiveFibers()
            }
        }
        mutex.locked {
            totalStartedFlows.inc()
            unfinishedFibers.countUp()
            notifyChangeObservers(StateMachineManager.Change.Add(fiber.logic))
        }
    }

    private fun verifyFlowLogicIsSuspendable(logic: FlowLogic<Any?>) {
        // Quasar requires (in Java 8) that at least the call method be annotated suspendable. Unfortunately, it's
        // easy to forget to add this when creating a new flow, so we check here to give the user a better error.
        //
        // The Kotlin compiler can sometimes generate a synthetic bridge method from a single call declaration, which
        // forwards to the void method and then returns Unit. However annotations do not get copied across to this
        // bridge, so we have to do a more complex scan here.
        val call = logic.javaClass.methods.first { !it.isSynthetic && it.name == "call" && it.parameterCount == 0 }
        if (call.getAnnotation(Suspendable::class.java) == null) {
            throw FlowException("${logic.javaClass.name}.call() is not annotated as @Suspendable. Please fix this.")
        }
    }

    private fun endAllFiberSessions(fiber: FlowStateMachineImpl<*>, result: Try<*>, propagated: Boolean) {
        openSessions.values.removeIf { session ->
            if (session.fiber == fiber) {
                session.endSession(fiber.context, (result as? Try.Failure)?.exception, propagated)
                true
            } else {
                false
            }
        }
    }

    private fun FlowSessionInternal.endSession(context: InvocationContext, exception: Throwable?, propagated: Boolean) {
        val initiatedState = state as? FlowSessionState.Initiated ?: return
        val sessionEnd = if (exception == null) {
            EndSessionMessage
        } else {
            val errorResponse = if (exception is FlowException && (!propagated || initiatingParty != null)) {
                // Only propagate this FlowException if our local flow threw it or it was propagated to us and we only
                // pass it down invocation chain to the flow that initiated us, not to flows we've started sessions with.
                exception
            } else {
                null
            }
            ErrorSessionMessage(errorResponse, 0)
        }
        sendSessionMessage(initiatedState.peerParty, ExistingSessionMessage(initiatedState.peerSessionId, sessionEnd), fiber)
        recentlyClosedSessions[ourSessionId] = initiatedState.peerParty
    }

    /**
     * Kicks off a brand new state machine of the given class.
     * The state machine will be persisted when it suspends, with automated restart if the StateMachineManager is
     * restarted with checkpointed state machines in the storage service.
     *
     * Note that you must be on the [executor] thread.
     */
    override fun <A> startFlow(flowLogic: FlowLogic<A>, context: InvocationContext): CordaFuture<FlowStateMachine<A>> {
        // TODO: Check that logic has @Suspendable on its call method.
        executor.checkOnThread()
        val fiber = database.transaction {
            val fiber = createFiber(flowLogic, context)
            updateCheckpoint(fiber)
            fiber
        }
        // If we are not started then our checkpoint will be picked up during start
        mutex.locked {
            if (started) {
                resumeFiber(fiber)
            }
        }
        return doneFuture(fiber)
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

        checkpointCheckerThread?.execute {
            // Immediately check that the checkpoint is valid by deserialising it. The idea is to plug any holes we have
            // in our testing by failing any test where unrestorable checkpoints are created.
            if (deserializeFiber(newCheckpoint, fiber.logger) == null) {
                unrestorableCheckpoints = true
            }
        }
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
            is Sleep -> processSleepRequest(ioRequest)
        }
    }

    private fun processSendRequest(ioRequest: SendRequest) {
        val retryId = if (ioRequest.message is InitialSessionMessage) {
            with(ioRequest.session) {
                openSessions[ourSessionId] = this
                if (retryable) ourSessionId.toLong else null
            }
        } else null
        sendSessionMessage(ioRequest.session.state.sendToParty, ioRequest.message, ioRequest.session.fiber, retryId)
        if (ioRequest !is ReceiveRequest) {
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

    private fun processSleepRequest(ioRequest: Sleep) {
        // Resume the fiber now we have checkpointed, so we can sleep on the Fiber.
        resumeFiber(ioRequest.fiber)
    }

    private fun sendSessionMessage(party: Party, message: SessionMessage, fiber: FlowStateMachineImpl<*>? = null, retryId: Long? = null) {
        val partyInfo = serviceHub.networkMapCache.getPartyInfo(party)
                ?: throw IllegalArgumentException("Don't know about party $party")
        val address = serviceHub.networkService.getAddressOfParty(partyInfo)
        val logger = fiber?.logger ?: logger
        logger.trace { "Sending $message to party $party @ $address" + if (retryId != null) " with retry $retryId" else "" }

        val serialized = try {
            message.serialize()
        } catch (e: Exception) {
            when (e) {
            // Handling Kryo and AMQP serialization problems. Unfortunately the two exception types do not share much of a common exception interface.
                is KryoException,
                is NotSerializableException -> {
                    if (message is ExistingSessionMessage && message.payload is ErrorSessionMessage && message.payload.flowException != null) {
                        logger.warn("Something in ${message.payload.flowException.javaClass.name} is not serialisable. " +
                                "Instead sending back an exception which is serialisable to ensure session end occurs properly.", e)
                        // The subclass may have overridden toString so we use that
                        val exMessage = message.payload.flowException.message
                        message.copy(payload = message.payload.copy(flowException = FlowException(exMessage))).serialize()
                    } else {
                        throw e
                    }
                }
                else -> throw e
            }
        }

        serviceHub.networkService.apply {
            send(createMessage(sessionTopic, serialized.bytes), address, retryId = retryId, additionalHeaders = message.additionalHeaders())
        }
    }
}

private fun SessionMessage.additionalHeaders(): Map<String, String> {
    return when (this) {
        is InitialSessionMessage -> mapOf(P2PMessagingHeaders.Type.KEY to P2PMessagingHeaders.Type.SESSION_INIT_VALUE)
        else -> emptyMap()
    }
}

class SessionRejectException(val rejectMessage: String, val logMessage: String) : CordaException(rejectMessage) {
    constructor(message: String) : this(message, message)
}
