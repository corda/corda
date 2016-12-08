package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSessionException
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.random63BitValue
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.trace
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.StateMachineManager.*
import net.corda.node.utilities.StrandLocalTransactionManager
import net.corda.node.utilities.createDatabaseTransaction
import net.corda.node.utilities.databaseTransaction
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
                              scheduler: FiberScheduler) : Fiber<R>("flow", scheduler), FlowStateMachine<R> {

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
    @Transient lateinit override var serviceHub: ServiceHubInternal
    @Transient internal lateinit var actionOnSuspend: (FlowIORequest) -> Unit
    @Transient internal lateinit var actionOnEnd: () -> Unit
    @Transient internal lateinit var database: Database
    @Transient internal var fromCheckpoint: Boolean = false
    @Transient internal var txTrampoline: Transaction? = null

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

    internal val openSessions = HashMap<Pair<FlowLogic<*>, Party>, FlowSession>()

    init {
        logic.fsm = this
        name = id.toString()
    }

    @Suspendable
    override fun run(): R {
        createTransaction()
        val result = try {
            logic.call()
        } catch (t: Throwable) {
            processException(t)
            commitTransaction()
            throw ExecutionException(t)
        }

        // This is to prevent actionOnEnd being called twice if it throws an exception
        actionOnEnd()
        _resultFuture?.set(result)
        commitTransaction()
        return result
    }

    private fun createTransaction() {
        // Make sure we have a database transaction
        createDatabaseTransaction(database)
        logger.trace { "Starting database transaction ${TransactionManager.currentOrNull()} on ${Strand.currentStrand()}." }
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
    override fun <T : Any> sendAndReceive(otherParty: Party,
                                          payload: Any,
                                          receiveType: Class<T>,
                                          sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
        val (session, new) = getSession(otherParty, sessionFlow, payload)
        val receivedSessionData = if (new) {
            // Only do a receive here as the session init has carried the payload
            receiveInternal<SessionData>(session)
        } else {
            val sendSessionData = createSessionData(session, payload)
            sendAndReceiveInternal<SessionData>(session, sendSessionData)
        }
        return UntrustworthyData(receiveType.cast(receivedSessionData.payload))
    }

    @Suspendable
    override fun <T : Any> receive(otherParty: Party,
                                   receiveType: Class<T>,
                                   sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
        val session = getSession(otherParty, sessionFlow, null).first
        val receivedSessionData = receiveInternal<SessionData>(session)
        return UntrustworthyData(receiveType.cast(receivedSessionData.payload))
    }

    @Suspendable
    override fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>) {
        val (session, new) = getSession(otherParty, sessionFlow, payload)
        if (!new) {
            // Don't send the payload again if it was already piggy-backed on a session init
            sendInternal(session, createSessionData(session, payload))
        }
    }

    private fun createSessionData(session: FlowSession, payload: Any): SessionData {
        val otherPartySessionId = session.otherPartySessionId
                ?: throw IllegalStateException("We've somehow held onto an unconfirmed session: $session")
        return SessionData(otherPartySessionId, payload)
    }

    @Suspendable
    private fun sendInternal(session: FlowSession, message: SessionMessage) {
        suspend(SendOnly(session, message))
    }

    @Suspendable
    private inline fun <reified M : SessionMessage> receiveInternal(session: FlowSession): M {
        return suspendAndExpectReceive(ReceiveOnly(session, M::class.java))
    }

    private inline fun <reified M : SessionMessage> sendAndReceiveInternal(session: FlowSession, message: SessionMessage): M {
        return suspendAndExpectReceive(SendAndReceive(session, message, M::class.java))
    }

    @Suspendable
    private fun getSession(otherParty: Party, sessionFlow: FlowLogic<*>, firstPayload: Any?): Pair<FlowSession, Boolean> {
        val session = openSessions[Pair(sessionFlow, otherParty)]
        return if (session != null) {
            Pair(session, false)
        } else {
            Pair(startNewSession(otherParty, sessionFlow, firstPayload), true)
        }
    }

    /**
     * Creates a new session. The provided [otherParty] can be an identity of any advertised service on the network,
     * and might be advertised by more than one node. Therefore we first choose a single node that advertises it
     * and use its *legal identity* for communication. At the moment a single node can compose its legal identity out of
     * multiple public keys, but we **don't support multiple nodes advertising the same legal identity**.
     */
    @Suspendable
    private fun startNewSession(otherParty: Party, sessionFlow: FlowLogic<*>, firstPayload: Any?): FlowSession {
        val node = serviceHub.networkMapCache.getRepresentativeNode(otherParty) ?: throw IllegalArgumentException("Don't know about party $otherParty")
        val nodeIdentity = node.legalIdentity
        logger.trace { "Initiating a new session with $nodeIdentity (representative of $otherParty)" }
        val session = FlowSession(sessionFlow, nodeIdentity, random63BitValue(), null)
        openSessions[Pair(sessionFlow, nodeIdentity)] = session
        val counterpartyFlow = sessionFlow.getCounterpartyMarker(nodeIdentity).name
        val sessionInit = SessionInit(session.ourSessionId, counterpartyFlow, firstPayload)
        val sessionInitResponse = sendAndReceiveInternal<SessionInitResponse>(session, sessionInit)
        if (sessionInitResponse is SessionConfirm) {
            session.otherPartySessionId = sessionInitResponse.initiatedSessionId
            return session
        } else {
            sessionInitResponse as SessionReject
            throw FlowSessionException("Party $nodeIdentity rejected session attempt: ${sessionInitResponse.errorMessage}")
        }
    }

    @Suspendable
    private fun <M : SessionMessage> suspendAndExpectReceive(receiveRequest: ReceiveRequest<M>): M {
        fun getReceivedMessage(): ExistingSessionMessage? = receiveRequest.session.receivedMessages.poll()

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
            getReceivedMessage()
                    ?: throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but got nothing: $receiveRequest")
        }

        if (receivedMessage is SessionEnd) {
            openSessions.values.remove(receiveRequest.session)
            throw FlowSessionException("Counterparty on ${receiveRequest.session.otherParty} has prematurely ended on $receiveRequest")
        } else if (receiveRequest.receiveType.isInstance(receivedMessage)) {
            return receiveRequest.receiveType.cast(receivedMessage)
        } else {
            throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but got $receivedMessage: $receiveRequest")
        }
    }

    @Suspendable
    private fun suspend(ioRequest: FlowIORequest) {
        // we have to pass the Thread local Transaction across via a transient field as the Fiber Park swaps them out.
        txTrampoline = TransactionManager.currentOrNull()
        StrandLocalTransactionManager.setThreadLocalTx(null)
        ioRequest.session.waitingForResponse = (ioRequest is ReceiveRequest<*>)
        parkAndSerialize { fiber, serializer ->
            logger.trace { "Suspended on $ioRequest" }
            // restore the Tx onto the ThreadLocal so that we can commit the ensuing checkpoint to the DB
            StrandLocalTransactionManager.setThreadLocalTx(txTrampoline)
            txTrampoline = null
            try {
                actionOnSuspend(ioRequest)
            } catch (t: Throwable) {
                // Do not throw exception again - Quasar completely bins it.
                logger.warn("Captured exception which was swallowed by Quasar for $logic at ${fiber.stackTrace.toList().joinToString("\n")}", t)
                // TODO When error handling is introduced, look into whether we should be deleting the checkpoint and
                // completing the Future
                processException(t)
            }
        }
        logger.trace { "Resumed from $ioRequest" }
        createTransaction()
    }

    private fun processException(t: Throwable) {
        // This can get called in actionOnSuspend *after* we commit the database transaction, so optionally open a new one here.
        databaseTransaction(database) {
            actionOnEnd()
            _resultFuture?.setException(t)
        }
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
