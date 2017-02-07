package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.random63BitValue
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.trace
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.StateMachineManager.FlowSession
import net.corda.node.services.statemachine.StateMachineManager.FlowSessionState
import net.corda.node.utilities.StrandLocalTransactionManager
import net.corda.node.utilities.createDatabaseTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ExecutionException

class FlowStateMachineImpl<R>(override val id: StateMachineRunId,
                              val logic: FlowLogic<R>,
                              scheduler: FiberScheduler) : Fiber<Unit>("flow", scheduler), FlowStateMachine<R> {
    companion object {
        // Used to work around a small limitation in Quasar.
        private val QUASAR_UNBLOCKER = run {
            val field = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
            field.isAccessible = true
            field.get(null)
        }

        /**
         * Return the current [FlowStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): FlowStateMachineImpl<*>? = Strand.currentStrand() as? FlowStateMachineImpl<*>
    }

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient override lateinit var serviceHub: ServiceHubInternal
    @Transient internal lateinit var database: Database
    @Transient internal lateinit var actionOnSuspend: (FlowIORequest) -> Unit
    @Transient internal lateinit var actionOnEnd: (Pair<FlowException, Boolean>?) -> Unit
    @Transient internal var fromCheckpoint: Boolean = false
    @Transient private var txTrampoline: Transaction? = null

    @Transient private var _logger: Logger? = null
    override val logger: Logger get() {
        return _logger ?: run {
            val l = LoggerFactory.getLogger(id.toString())
            _logger = l
            return l
        }
    }

    @Transient private var _resultFuture: SettableFuture<R>? = SettableFuture.create<R>()
    /** This future will complete when the call method returns. */
    override val resultFuture: ListenableFuture<R> get() {
        return _resultFuture ?: run {
            val f = SettableFuture.create<R>()
            _resultFuture = f
            return f
        }
    }

    // This state IS serialised, as we need it to know what the fiber is waiting for.
    internal val openSessions = HashMap<Pair<FlowLogic<*>, Party>, FlowSession>()
    internal var waitingForLedgerCommitOf: SecureHash? = null

    init {
        logic.stateMachine = this
        name = id.toString()
    }

    @Suspendable
    override fun run() {
        createTransaction()
        val result = try {
            logic.call()
        } catch (e: FlowException) {
            // Check if the FlowException was propagated by looking at where the stack trace originates (see suspendAndExpectReceive).
            val propagated = e.stackTrace[0].className == javaClass.name
            actionOnEnd(Pair(e, propagated))
            _resultFuture?.setException(e)
            return
        } catch (t: Throwable) {
            actionOnEnd(null)
            _resultFuture?.setException(t)
            throw ExecutionException(t)
        }

        // Only sessions which have a single send and nothing else will block here
        openSessions.values
                .filter { it.state is FlowSessionState.Initiating }
                .forEach { it.waitForConfirmation() }
        // This is to prevent actionOnEnd being called twice if it throws an exception
        actionOnEnd(null)
        _resultFuture?.set(result)
    }

    private fun createTransaction() {
        // Make sure we have a database transaction
        createDatabaseTransaction(database)
        logger.trace { "Starting database transaction ${TransactionManager.currentOrNull()} on ${Strand.currentStrand()}" }
    }

    internal fun commitTransaction() {
        val transaction = TransactionManager.current()
        try {
            logger.trace { "Commiting database transaction $transaction on ${Strand.currentStrand()}." }
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
    override fun <T : Any> sendAndReceive(receiveType: Class<T>,
                                          otherParty: Party,
                                          payload: Any,
                                          sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
        val session = getConfirmedSession(otherParty, sessionFlow)
        return if (session == null) {
            // Only do a receive here as the session init has carried the payload
            receiveInternal<SessionData>(startNewSession(otherParty, sessionFlow, payload, waitForConfirmation = true))
        } else {
            sendAndReceiveInternal<SessionData>(session, createSessionData(session, payload))
        }.checkPayloadIs(receiveType)
    }

    @Suspendable
    override fun <T : Any> receive(receiveType: Class<T>,
                                   otherParty: Party,
                                   sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
        val session = getConfirmedSession(otherParty, sessionFlow) ?: startNewSession(otherParty, sessionFlow, null, waitForConfirmation = true)
        return receiveInternal<SessionData>(session).checkPayloadIs(receiveType)
    }

    @Suspendable
    override fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>) {
        val session = getConfirmedSession(otherParty, sessionFlow)
        if (session == null) {
            // Don't send the payload again if it was already piggy-backed on a session init
            startNewSession(otherParty, sessionFlow, payload, waitForConfirmation = false)
        } else {
            sendInternal(session, createSessionData(session, payload))
        }
    }

    /**
     * This method will suspend the state machine and wait for incoming session init response from other party.
     */
    @Suspendable
    private fun FlowSession.waitForConfirmation() {
        val (peerParty, sessionInitResponse) = receiveInternal<SessionInitResponse>(this)
        if (sessionInitResponse is SessionConfirm) {
            state = FlowSessionState.Initiated(peerParty, sessionInitResponse.initiatedSessionId)
        } else {
            sessionInitResponse as SessionReject
            throw FlowException("Party ${state.sendToParty} rejected session request: ${sessionInitResponse.errorMessage}")
        }
    }

    @Suspendable
    override fun waitForLedgerCommit(hash: SecureHash, sessionFlow: FlowLogic<*>): SignedTransaction {
        waitingForLedgerCommitOf = hash
        logger.info("Waiting for transaction $hash to commit")
        suspend(WaitForLedgerCommit(hash, sessionFlow.stateMachine as FlowStateMachineImpl<*>))
        logger.info("Transaction $hash has committed to the ledger, resuming")
        val stx = serviceHub.storageService.validatedTransactions.getTransaction(hash)
        return stx ?: throw IllegalStateException("We were resumed after waiting for $hash but it wasn't found in our local storage")
    }

    private fun createSessionData(session: FlowSession, payload: Any): SessionData {
        val sessionState = session.state
        val peerSessionId = when (sessionState) {
            is FlowSessionState.Initiating -> throw IllegalStateException("We've somehow held onto an unconfirmed session: $session")
            is FlowSessionState.Initiated -> sessionState.peerSessionId
        }
        return SessionData(peerSessionId, payload)
    }

    @Suspendable
    private fun sendInternal(session: FlowSession, message: SessionMessage) {
        suspend(SendOnly(session, message))
    }

    private inline fun <reified M : ExistingSessionMessage> receiveInternal(session: FlowSession): ReceivedSessionMessage<M> {
        return suspendAndExpectReceive(ReceiveOnly(session, M::class.java))
    }

    private inline fun <reified M : ExistingSessionMessage> sendAndReceiveInternal(
            session: FlowSession,
            message: SessionMessage): ReceivedSessionMessage<M> {
        return suspendAndExpectReceive(SendAndReceive(session, message, M::class.java))
    }

    @Suspendable
    private fun getConfirmedSession(otherParty: Party, sessionFlow: FlowLogic<*>): FlowSession? {
        return openSessions[Pair(sessionFlow, otherParty)]?.apply {
            if (state is FlowSessionState.Initiating) {
                // Session still initiating, try to retrieve the init response.
                waitForConfirmation()
            }
        }
    }

    /**
     * Creates a new session. The provided [otherParty] can be an identity of any advertised service on the network,
     * and might be advertised by more than one node. Therefore we first choose a single node that advertises it
     * and use its *legal identity* for communication. At the moment a single node can compose its legal identity out of
     * multiple public keys, but we **don't support multiple nodes advertising the same legal identity**.
     */
    @Suspendable
    private fun startNewSession(otherParty: Party, sessionFlow: FlowLogic<*>, firstPayload: Any?, waitForConfirmation: Boolean): FlowSession {
        logger.trace { "Initiating a new session with $otherParty" }
        val session = FlowSession(sessionFlow, random63BitValue(), null, FlowSessionState.Initiating(otherParty))
        openSessions[Pair(sessionFlow, otherParty)] = session
        val counterpartyFlow = sessionFlow.getCounterpartyMarker(otherParty).name
        val sessionInit = SessionInit(session.ourSessionId, counterpartyFlow, firstPayload)
        sendInternal(session, sessionInit)
        if (waitForConfirmation) {
            session.waitForConfirmation()
        }
        return session
    }

    @Suspendable
    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun <M : ExistingSessionMessage> suspendAndExpectReceive(receiveRequest: ReceiveRequest<M>): ReceivedSessionMessage<M> {
        val session = receiveRequest.session
        fun getReceivedMessage(): ReceivedSessionMessage<ExistingSessionMessage>? = session.receivedMessages.poll()

        val polledMessage = getReceivedMessage()
        val receivedMessage = if (polledMessage != null) {
            if (receiveRequest is SendAndReceive) {
                // We've already received a message but we suspend so that the send can be performed
                suspend(receiveRequest)
            }
            polledMessage
        } else {
            // Suspend while we wait for a receive
            suspend(receiveRequest)
            getReceivedMessage() ?:
                    throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but instead " +
                            "got nothing for $receiveRequest")
        }

        if (receiveRequest.receiveType.isInstance(receivedMessage.message)) {
            return receivedMessage as ReceivedSessionMessage<M>
        } else if (receivedMessage.message is SessionEnd) {
            openSessions.values.remove(session)
            if (receivedMessage.message.errorResponse != null) {
                (receivedMessage.message.errorResponse as java.lang.Throwable).fillInStackTrace()
                throw receivedMessage.message.errorResponse
            } else {
                throw FlowSessionException("${session.state.sendToParty} has ended their flow but we were expecting " +
                        "to receive ${receiveRequest.receiveType.simpleName} from them")
            }
        } else {
            throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but instead got " +
                    "${receivedMessage.message} for $receiveRequest")
        }
    }

    @Suspendable
    private fun suspend(ioRequest: FlowIORequest) {
        // We have to pass the thread local database transaction across via a transient field as the fiber park
        // swaps them out.
        txTrampoline = TransactionManager.currentOrNull()
        StrandLocalTransactionManager.setThreadLocalTx(null)
        if (ioRequest is SessionedFlowIORequest)
            ioRequest.session.waitingForResponse = (ioRequest is ReceiveRequest<*>)

        var exceptionDuringSuspend: Throwable? = null
        parkAndSerialize { fiber, serializer ->
            logger.trace { "Suspended on $ioRequest" }
            // restore the Tx onto the ThreadLocal so that we can commit the ensuing checkpoint to the DB
            try {
                StrandLocalTransactionManager.setThreadLocalTx(txTrampoline)
                txTrampoline = null
                actionOnSuspend(ioRequest)
            } catch (t: Throwable) {
                // Quasar does not terminate the fiber properly if an exception occurs during a suspend. We have to
                // resume the fiber just so that we can throw it when it's running.
                exceptionDuringSuspend = t
                resume(scheduler)
            }
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
                logger.trace("Resumed")
                Fiber.unpark(this, QUASAR_UNBLOCKER)
            }
        } catch (t: Throwable) {
            logger.error("Error during resume", t)
        }
    }
}
