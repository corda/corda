package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Fiber.parkAndSerialize
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.primitives.Primitives
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.*
import net.corda.node.services.api.FlowAppAuditEvent
import net.corda.node.services.api.FlowPermissionAuditEvent
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.logging.pushToLoggingContext
import net.corda.node.services.statemachine.FlowSessionState.Initiating
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class FlowPermissionException(message: String) : FlowException(message)

class FlowStateMachineImpl<R>(override val id: StateMachineRunId,
                              override val logic: FlowLogic<R>,
                              scheduler: FiberScheduler,
                              val ourIdentity: Party,
                              override val context: InvocationContext) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R> {

    companion object {
        // Used to work around a small limitation in Quasar.
        private val QUASAR_UNBLOCKER = Fiber::class.staticField<Any>("SERIALIZER_BLOCKER").value

        /**
         * Return the current [FlowStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): FlowStateMachineImpl<*>? = Strand.currentStrand() as? FlowStateMachineImpl<*>
    }

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient override lateinit var serviceHub: ServiceHubInternal
    @Transient override lateinit var ourIdentityAndCert: PartyAndCertificate
    @Transient internal lateinit var database: CordaPersistence
    @Transient internal lateinit var actionOnSuspend: (FlowIORequest) -> Unit
    @Transient internal lateinit var actionOnEnd: (Try<R>, Boolean) -> Unit
    @Transient internal var fromCheckpoint: Boolean = false
    @Transient private var txTrampoline: DatabaseTransaction? = null

    /**
     * Return the logger for this state machine. The logger name incorporates [id] and so including it in the log message
     * is not necessary.
     */
    override val logger: Logger = LoggerFactory.getLogger("net.corda.flow.$id")
    @Transient private var resultFutureTransient: OpenFuture<R>? = openFuture()
    private val _resultFuture get() = resultFutureTransient ?: openFuture<R>().also { resultFutureTransient = it }
    /** This future will complete when the call method returns. */
    override val resultFuture: CordaFuture<R> get() = _resultFuture
    // This state IS serialised, as we need it to know what the fiber is waiting for.
    internal val openSessions = HashMap<Pair<FlowLogic<*>, Party>, FlowSessionInternal>()
    internal var waitingForResponse: WaitingRequest? = null
    internal var hasSoftLockedStates: Boolean = false
        set(value) {
            if (value) field = value else throw IllegalArgumentException("Can only set to true")
        }

    init {
        logic.stateMachine = this
    }

    @Suspendable
    override fun run() {
        createTransaction()
        logger.debug { "Calling flow: $logic" }
        val startTime = System.nanoTime()
        val result = try {
            val r = logic.call()
            // Only sessions which have done a single send and nothing else will block here
            openSessions.values
                    .filter { it.state is Initiating }
                    .forEach { it.waitForConfirmation() }
            r
        } catch (e: FlowException) {
            recordDuration(startTime, success = false)
            // Check if the FlowException was propagated by looking at where the stack trace originates (see suspendAndExpectReceive).
            val propagated = e.stackTrace[0].className == javaClass.name
            processException(e, propagated)
            logger.warn(if (propagated) "Flow ended due to receiving exception" else "Flow finished with exception", e)
            return
        } catch (t: Throwable) {
            recordDuration(startTime, success = false)
            logger.warn("Terminated by unexpected exception", t)
            processException(t, false)
            return
        }

        recordDuration(startTime)
        // This is to prevent actionOnEnd being called twice if it throws an exception
        actionOnEnd(Try.Success(result), false)
        _resultFuture.set(result)
        logic.progressTracker?.currentStep = ProgressTracker.DONE
        logger.debug { "Flow finished with result ${result.toString().abbreviate(300)}" }
    }

    private fun createTransaction() {
        // Make sure we have a database transaction
        database.createTransaction()
        logger.trace { "Starting database transaction $contextTransactionOrNull on ${Strand.currentStrand()}" }
    }

    private fun processException(exception: Throwable, propagated: Boolean) {
        actionOnEnd(Try.Failure(exception), propagated)
        _resultFuture.setException(exception)
        logic.progressTracker?.endWithError(exception)
    }

    internal fun commitTransaction() {
        val transaction = contextTransaction
        try {
            logger.trace { "Committing database transaction $transaction on ${Strand.currentStrand()}." }
            transaction.commit()
        } catch (e: SQLException) {
            // TODO: we will get here if the database is not available.  Think about how to shutdown and restart cleanly.
            logger.error("Transaction commit failed: ${e.message}", e)
            System.exit(1)
        } finally {
            transaction.close()
        }
    }

    @Suspendable
    override fun initiateFlow(otherParty: Party, sessionFlow: FlowLogic<*>): FlowSession {
        val sessionKey = Pair(sessionFlow, otherParty)
        if (openSessions.containsKey(sessionKey)) {
            throw IllegalStateException(
                    "Attempted to initiateFlow() twice in the same InitiatingFlow $sessionFlow for the same party " +
                            "$otherParty. This isn't supported in this version of Corda. Alternatively you may " +
                            "initiate a new flow by calling initiateFlow() in an " +
                            "@${InitiatingFlow::class.java.simpleName} sub-flow."
            )
        }
        val flowSession = FlowSessionImpl(otherParty)
        createNewSession(otherParty, flowSession, sessionFlow)
        flowSession.stateMachine = this
        flowSession.sessionFlow = sessionFlow
        return flowSession
    }

    @Suspendable
    override fun getFlowInfo(otherParty: Party, sessionFlow: FlowLogic<*>, maySkipCheckpoint: Boolean): FlowInfo {
        val state = getConfirmedSession(otherParty, sessionFlow).state as FlowSessionState.Initiated
        return state.context
    }

    @Suspendable
    override fun <T : Any> sendAndReceive(receiveType: Class<T>,
                                          otherParty: Party,
                                          payload: Any,
                                          sessionFlow: FlowLogic<*>,
                                          retrySend: Boolean,
                                          maySkipCheckpoint: Boolean): UntrustworthyData<T> {
        requireNonPrimitive(receiveType)
        logger.debug { "sendAndReceive(${receiveType.name}, $otherParty, ${payload.toString().abbreviate(300)}) ..." }
        val session = getConfirmedSessionIfPresent(otherParty, sessionFlow)
        val receivedSessionMessage: ReceivedSessionMessage = if (session == null) {
            val newSession = initiateSession(otherParty, sessionFlow, payload, waitForConfirmation = true, retryable = retrySend)
            // Only do a receive here as the session init has carried the payload
            receiveInternal(newSession, receiveType)
        } else {
            val sendData = createSessionData(session, payload)
            sendAndReceiveInternal(session, sendData, receiveType)
        }
        val sessionData = receivedSessionMessage.message.checkDataSessionMessage()
        logger.debug { "Received ${sessionData.payload.toString().abbreviate(300)}" }
        return sessionData.checkPayloadIs(receiveType)
    }

    private fun ExistingSessionMessage.checkDataSessionMessage(): DataSessionMessage {
        when (payload) {
            is DataSessionMessage -> {
                return payload
            }
            else -> {
                throw IllegalStateException("Was expecting ${DataSessionMessage::class.java.simpleName} but got ${payload.javaClass.simpleName} instead")
            }
        }
    }

    @Suspendable
    override fun <T : Any> receive(receiveType: Class<T>,
                                   otherParty: Party,
                                   sessionFlow: FlowLogic<*>,
                                   maySkipCheckpoint: Boolean): UntrustworthyData<T> {
        requireNonPrimitive(receiveType)
        logger.debug { "receive(${receiveType.name}, $otherParty) ..." }
        val session = getConfirmedSession(otherParty, sessionFlow)
        val receivedSessionMessage = receiveInternal(session, receiveType).message.checkDataSessionMessage()
        logger.debug { "Received ${receivedSessionMessage.payload.toString().abbreviate(300)}" }
        return receivedSessionMessage.checkPayloadIs(receiveType)
    }

    private fun requireNonPrimitive(receiveType: Class<*>) {
        require(!receiveType.isPrimitive) {
            "Use the wrapper type ${Primitives.wrap(receiveType).name} instead of the primitive $receiveType.class"
        }
    }

    @Suspendable
    override fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>, maySkipCheckpoint: Boolean) {
        logger.debug { "send($otherParty, ${payload.toString().abbreviate(300)})" }
        val session = getConfirmedSessionIfPresent(otherParty, sessionFlow)
        if (session == null) {
            // Don't send the payload again if it was already piggy-backed on a session init
            initiateSession(otherParty, sessionFlow, payload, waitForConfirmation = false)
        } else {
            sendInternal(session, ExistingSessionMessage(session.getPeerSessionId(), createSessionData(session, payload)))
        }
    }

    @Suspendable
    override fun waitForLedgerCommit(hash: SecureHash, sessionFlow: FlowLogic<*>, maySkipCheckpoint: Boolean): SignedTransaction {
        logger.debug { "waitForLedgerCommit($hash) ..." }
        suspend(WaitForLedgerCommit(hash, sessionFlow.stateMachine as FlowStateMachineImpl<*>))
        val stx = serviceHub.validatedTransactions.getTransaction(hash)
        if (stx != null) {
            logger.debug { "Transaction $hash committed to ledger" }
            return stx
        }

        // If the tx isn't committed then we may have been resumed due to an session ending in an error
        for (session in openSessions.values) {
            for (receivedMessage in session.receivedMessages) {
                if (receivedMessage.message.payload is ErrorSessionMessage) {
                    session.erroredEnd(receivedMessage.message.payload.flowException)
                }
            }
        }
        throw IllegalStateException("We were resumed after waiting for $hash but it wasn't found in our local storage")
    }

    // Provide a mechanism to sleep within a Strand without locking any transactional state.
    // This checkpoints, since we cannot undo any database writes up to this point.
    @Suspendable
    override fun sleepUntil(until: Instant) {
        suspend(Sleep(until, this))
    }

    // TODO Dummy implementation of access to application specific permission controls and audit logging
    override fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>) {
        val permissionGranted = true // TODO define permission control service on ServiceHubInternal and actually check authorization.
        val checkPermissionEvent = FlowPermissionAuditEvent(
                serviceHub.clock.instant(),
                context,
                "Flow Permission Required: $permissionName",
                extraAuditData,
                logic.javaClass,
                id,
                permissionName,
                permissionGranted)
        serviceHub.auditService.recordAuditEvent(checkPermissionEvent)
        @Suppress("ConstantConditionIf")
        if (!permissionGranted) {
            throw FlowPermissionException("User ${context.principal()} not permissioned for $permissionName on flow $id")
        }
    }

    // TODO Dummy implementation of access to application specific audit logging
    override fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>) {
        val flowAuditEvent = FlowAppAuditEvent(
                serviceHub.clock.instant(),
                context,
                comment,
                extraAuditData,
                logic.javaClass,
                id,
                eventType)
        serviceHub.auditService.recordAuditEvent(flowAuditEvent)
    }

    @Suspendable
    override fun flowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot? {
        return FlowStackSnapshotFactory.instance.getFlowStackSnapshot(flowClass)
    }

    override fun persistFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>) {
        FlowStackSnapshotFactory.instance.persistAsJsonFile(flowClass, serviceHub.configuration.baseDirectory, id)
    }

    @Suspendable
    override fun receiveAll(sessions: Map<FlowSession, Class<out Any>>, sessionFlow: FlowLogic<*>): Map<FlowSession, UntrustworthyData<Any>> {
        val requests = ArrayList<ReceiveOnly>()
        for ((session, receiveType) in sessions) {
            val sessionInternal = getConfirmedSession(session.counterparty, sessionFlow)
            requests.add(ReceiveOnly(sessionInternal, receiveType))
        }
        val receivedMessages = ReceiveAll(requests).suspendAndExpectReceive(suspend)
        val result = LinkedHashMap<FlowSession, UntrustworthyData<Any>>()
        for ((sessionInternal, requestAndMessage) in receivedMessages) {
            val message = requestAndMessage.message.confirmNoError(requestAndMessage.request.session)
            result[sessionInternal.flowSession] = message.checkDataSessionMessage().checkPayloadIs(
                    requestAndMessage.request.userReceiveType as Class<out Any>
            )
        }
        return result
    }

    internal fun pushToLoggingContext() = context.pushToLoggingContext()

    /**
     * This method will suspend the state machine and wait for incoming session init response from other party.
     */
    @Suspendable
    private fun FlowSessionInternal.waitForConfirmation() {
        val sessionInitResponse = receiveInternal(this, null)
        val payload = sessionInitResponse.message.payload
        when (payload) {
            is ConfirmSessionMessage -> {
                state = FlowSessionState.Initiated(
                        sessionInitResponse.
                        peerParty,
                        payload.initiatedSessionId,
                        payload.initiatedFlowInfo)
            }
            is RejectSessionMessage -> {
                throw UnexpectedFlowEndException("Party ${state.sendToParty} rejected session request: ${payload.message}")
            }
            else -> {
                throw IllegalStateException("Was expecting ${ConfirmSessionMessage::class.java.simpleName} but got ${payload.javaClass.simpleName} instead")
            }
        }
    }

    private fun createSessionData(session: FlowSessionInternal, payload: Any): DataSessionMessage {
        return DataSessionMessage(payload.serialize(context = SerializationDefaults.P2P_CONTEXT))
    }

    @Suspendable
    private fun sendInternal(session: FlowSessionInternal, message: SessionMessage) = suspend(SendOnly(session, message))

    @Suspendable
    private fun receiveInternal(
            session: FlowSessionInternal,
            userReceiveType: Class<*>?): ReceivedSessionMessage {
        return waitForMessage(ReceiveOnly(session, userReceiveType))
    }

    @Suspendable
    private fun sendAndReceiveInternal(
            session: FlowSessionInternal,
            message: DataSessionMessage,
            userReceiveType: Class<*>?): ReceivedSessionMessage {
        val sessionMessage = ExistingSessionMessage(session.getPeerSessionId(), message)
        return waitForMessage(SendAndReceive(session, sessionMessage, userReceiveType))
    }

    @Suspendable
    private fun getConfirmedSessionIfPresent(otherParty: Party, sessionFlow: FlowLogic<*>): FlowSessionInternal? {
        val session = openSessions[Pair(sessionFlow, otherParty)] ?: return null
        return when (session.state) {
            is FlowSessionState.Uninitiated -> null
            is FlowSessionState.Initiating -> {
                session.waitForConfirmation()
                session
            }
            is FlowSessionState.Initiated -> session
        }
    }

    @Suspendable
    private fun getConfirmedSession(otherParty: Party, sessionFlow: FlowLogic<*>): FlowSessionInternal {
        return getConfirmedSessionIfPresent(otherParty, sessionFlow) ?:
                initiateSession(otherParty, sessionFlow, null, waitForConfirmation = true)
    }

    private fun createNewSession(
            otherParty: Party,
            flowSession: FlowSession,
            sessionFlow: FlowLogic<*>
    ) {
        logger.trace { "Creating a new session with $otherParty" }
        val session = FlowSessionInternal(sessionFlow, flowSession, SessionId.createRandom(newSecureRandom()), null, FlowSessionState.Uninitiated(otherParty))
        openSessions[Pair(sessionFlow, otherParty)] = session
    }

    @Suspendable
    private fun initiateSession(
            otherParty: Party,
            sessionFlow: FlowLogic<*>,
            firstPayload: Any?,
            waitForConfirmation: Boolean,
            retryable: Boolean = false
    ): FlowSessionInternal {
        val session = openSessions[Pair(sessionFlow, otherParty)] ?: throw IllegalStateException("Expected an Uninitiated session for $otherParty")
        val state = session.state as? FlowSessionState.Uninitiated ?: throw IllegalStateException("Tried to initiate a session $session, but it's already initiating/initiated")
        logger.trace { "Initiating a new session with ${state.otherParty}" }
        session.state = FlowSessionState.Initiating(state.otherParty)
        session.retryable = retryable
        val (version, initiatingFlowClass) = session.flow.javaClass.flowVersionAndInitiatingClass
        val payloadBytes = firstPayload?.serialize(context = SerializationDefaults.P2P_CONTEXT)
        logger.info("Initiating flow session with party ${otherParty.name}. Session id for tracing purposes is ${session.ourSessionId}.")
        val sessionInit = InitialSessionMessage(session.ourSessionId, newSecureRandom().nextLong(), initiatingFlowClass.name, version, session.flow.javaClass.appName, payloadBytes)
        sendInternal(session, sessionInit)
        if (waitForConfirmation) {
            session.waitForConfirmation()
        }
        return session
    }

    @Suspendable
    private fun waitForMessage(receiveRequest: ReceiveRequest): ReceivedSessionMessage {
        val receivedMessage = receiveRequest.suspendAndExpectReceive()
        receivedMessage.message.confirmNoError(receiveRequest.session)
        return receivedMessage
    }

    private val suspend : ReceiveAll.Suspend = object : ReceiveAll.Suspend {
        @Suspendable
        override fun invoke(request: FlowIORequest) {
            suspend(request)
        }
    }

    @Suspendable
    private fun ReceiveRequest.suspendAndExpectReceive(): ReceivedSessionMessage {
        val polledMessage = session.receivedMessages.poll()
        return if (polledMessage != null) {
            if (this is SendAndReceive) {
                // Since we've already received the message, we downgrade to a send only to get the payload out and not
                // inadvertently block
                suspend(SendOnly(session, message))
            }
            polledMessage
        } else {
            // Suspend while we wait for a receive
            suspend(this)
            session.receivedMessages.poll() ?:
                    throw IllegalStateException("Was expecting a message but instead got nothing for $this")
        }
    }

    private fun ExistingSessionMessage.confirmNoError(session: FlowSessionInternal): ExistingSessionMessage {
        when (payload) {
            is ConfirmSessionMessage,
            is DataSessionMessage -> {
                return this
            }
            is ErrorSessionMessage -> {
                openSessions.values.remove(session)
                session.erroredEnd(payload.flowException)
            }
            is RejectSessionMessage -> {
                session.erroredEnd(UnexpectedFlowEndException("Counterparty sent session rejection message at unexpected time with message ${payload.message}"))
            }
            EndSessionMessage -> {
                openSessions.values.remove(session)
                throw UnexpectedFlowEndException("Counterparty flow on ${session.state.sendToParty} has completed without " +
                        "sending data")
            }
        }
    }

    private fun FlowSessionInternal.erroredEnd(exception: Throwable?): Nothing {
        if (exception != null) {
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            exception.fillInStackTrace()
            throw exception
        } else {
            throw UnexpectedFlowEndException("Counterparty flow on ${state.sendToParty} had an internal error and has terminated")
        }
    }

    @Suspendable
    private fun suspend(ioRequest: FlowIORequest) {
        // We have to pass the thread local database transaction across via a transient field as the fiber park
        // swaps them out.
        txTrampoline = contextTransactionOrNull
        contextTransactionOrNull = null
        if (ioRequest is WaitingRequest)
            waitingForResponse = ioRequest

        var exceptionDuringSuspend: Throwable? = null
        parkAndSerialize { _, _ ->
            logger.trace { "Suspended on $ioRequest" }
            // restore the Tx onto the ThreadLocal so that we can commit the ensuing checkpoint to the DB
            try {
                contextTransactionOrNull = txTrampoline
                txTrampoline = null
                actionOnSuspend(ioRequest)
            } catch (t: Throwable) {
                // Quasar does not terminate the fiber properly if an exception occurs during a suspend. We have to
                // resume the fiber just so that we can throw it when it's running.
                exceptionDuringSuspend = t
                logger.trace("Resuming so fiber can it terminate with the exception thrown during suspend process", t)
                resume(scheduler)
            }
        }

        if (exceptionDuringSuspend == null && ioRequest is Sleep) {
            // Sleep on the fiber.  This will not sleep if it's in the past.
            Strand.sleep(Duration.between(Instant.now(), ioRequest.until).toNanos(), TimeUnit.NANOSECONDS)
        }
        createTransaction()
        // TODO Now that we're throwing outside of the suspend the FlowLogic can catch it. We need Quasar to terminate
        // the fiber when exceptions occur inside a suspend.
        exceptionDuringSuspend?.let { throw it }
        logger.trace { "Resumed from $ioRequest" }
    }

    internal fun resume(scheduler: FiberScheduler) {
        try {
            if (fromCheckpoint) {
                logger.info("Resumed from checkpoint")
                fromCheckpoint = false
                Fiber.unparkDeserialized(this, scheduler)
            } else if (state == State.NEW) {
                logger.trace("Started")
                start()
            } else {
                Fiber.unpark(this, QUASAR_UNBLOCKER)
            }
        } catch (t: Throwable) {
            logger.error("Error during resume", t)
        }
    }

    /**
     * Records the duration of this flow – from call() to completion or failure.
     * Note that the duration will include the time the flow spent being parked, and not just the total
     * execution time.
     */
    private fun recordDuration(startTime: Long, success: Boolean = true) {
        val timerName = "FlowDuration.${if (success) "Success" else "Failure"}.${logic.javaClass.name}"
        val timer = serviceHub.monitoringService.metrics.timer(timerName)
        // Start time gets serialized along with the fiber when it suspends
        val duration = System.nanoTime() - startTime
        timer.update(duration, TimeUnit.NANOSECONDS)
    }
}

val Class<out FlowLogic<*>>.flowVersionAndInitiatingClass: Pair<Int, Class<out FlowLogic<*>>>
    get() {
        var current: Class<*> = this
        var found: Pair<Int, Class<out FlowLogic<*>>>? = null
        while (true) {
            val annotation = current.getDeclaredAnnotation(InitiatingFlow::class.java)
            if (annotation != null) {
                if (found != null) throw IllegalArgumentException("${InitiatingFlow::class.java.name} can only be annotated once")
                require(annotation.version > 0) { "Flow versions have to be greater or equal to 1" }
                found = annotation.version to uncheckedCast(current)
            }
            current = current.superclass
                    ?: return found
                    ?: throw IllegalArgumentException("$name, as a flow that initiates other flows, must be annotated with " +
                    "${InitiatingFlow::class.java.name}. See https://docs.corda.net/api-flows.html#flowlogic-annotations.")
        }
    }

val Class<out FlowLogic<*>>.appName: String
    get() {
        val jarFile = protectionDomain.codeSource.location.toPath()
        return if (jarFile.isRegularFile() && jarFile.toString().endsWith(".jar")) {
            jarFile.fileName.toString().removeSuffix(".jar")
        } else {
            "<unknown>"
        }
    }

fun <T : Any> DataSessionMessage.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    val payloadData: T = try {
        val serializer = SerializationDefaults.SERIALIZATION_FACTORY
        serializer.deserialize(payload, type, SerializationDefaults.P2P_CONTEXT)
    } catch (ex: Exception) {
        throw IOException("Payload invalid", ex)
    }
    return type.castIfPossible(payloadData)?.let { UntrustworthyData(it) } ?:
            throw UnexpectedFlowEndException("We were expecting a ${type.name} but we instead got a " +
                    "${payloadData.javaClass.name} (${payloadData})")
}
