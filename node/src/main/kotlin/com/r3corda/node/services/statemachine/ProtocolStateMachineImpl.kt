package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolSessionException
import com.r3corda.core.protocols.ProtocolStateMachine
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.random63BitValue
import com.r3corda.core.rootCause
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.statemachine.StateMachineManager.*
import com.r3corda.node.utilities.createDatabaseTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * A ProtocolStateMachine instance is a suspendable fiber that delegates all actual logic to a [ProtocolLogic] instance.
 * For any given flow there is only one PSM, even if that protocol invokes subprotocols.
 *
 * These classes are created by the [StateMachineManager] when a new protocol is started at the topmost level. If
 * a protocol invokes a sub-protocol, then it will pass along the PSM to the child. The call method of the topmost
 * logic element gets to return the value that the entire state machine resolves to.
 */
class ProtocolStateMachineImpl<R>(override val id: StateMachineRunId,
                                  val logic: ProtocolLogic<R>,
                                  scheduler: FiberScheduler,
                                  private val loggerName: String)
: Fiber<R>("protocol", scheduler), ProtocolStateMachine<R> {

    companion object {
        // Used to work around a small limitation in Quasar.
        private val QUASAR_UNBLOCKER = run {
            val field = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
            field.isAccessible = true
            field.get(null)
        }

        /**
         * Return the current [ProtocolStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): ProtocolStateMachineImpl<*>? = Strand.currentStrand() as? ProtocolStateMachineImpl<*>
    }

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient lateinit override var serviceHub: ServiceHubInternal
    @Transient internal lateinit var actionOnSuspend: (ProtocolIORequest) -> Unit
    @Transient internal lateinit var actionOnEnd: () -> Unit
    @Transient internal lateinit var database: Database
    @Transient internal var fromCheckpoint: Boolean = false

    @Transient private var _logger: Logger? = null
    override val logger: Logger get() {
        return _logger ?: run {
            val l = LoggerFactory.getLogger(loggerName)
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

    internal val openSessions = HashMap<Pair<ProtocolLogic<*>, Party>, ProtocolSession>()

    init {
        logic.psm = this
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

    private fun commitTransaction() {
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
                                          sessionProtocol: ProtocolLogic<*>): UntrustworthyData<T> {
        val session = getSession(otherParty, sessionProtocol)
        val sendSessionData = createSessionData(session, payload)
        val receivedSessionData = sendAndReceiveInternal(session, sendSessionData, SessionData::class.java)
        return UntrustworthyData(receiveType.cast(receivedSessionData.payload))
    }

    @Suspendable
    override fun <T : Any> receive(otherParty: Party,
                                   receiveType: Class<T>,
                                   sessionProtocol: ProtocolLogic<*>): UntrustworthyData<T> {
        val receivedSessionData = receiveInternal(getSession(otherParty, sessionProtocol), SessionData::class.java)
        return UntrustworthyData(receiveType.cast(receivedSessionData.payload))
    }

    @Suspendable
    override fun send(otherParty: Party, payload: Any, sessionProtocol: ProtocolLogic<*>) {
        val session = getSession(otherParty, sessionProtocol)
        val sendSessionData = createSessionData(session, payload)
        sendInternal(session, sendSessionData)
    }

    private fun createSessionData(session: ProtocolSession, payload: Any): SessionData {
        val otherPartySessionId = session.otherPartySessionId
                ?: throw IllegalStateException("We've somehow held onto an unconfirmed session: $session")
        return SessionData(otherPartySessionId, payload)
    }

    @Suspendable
    private fun sendInternal(session: ProtocolSession, message: SessionMessage) {
        suspend(SendOnly(session, message))
    }

    @Suspendable
    private fun <T : SessionMessage> receiveInternal(session: ProtocolSession, receiveType: Class<T>): T {
        return suspendAndExpectReceive(ReceiveOnly(session, receiveType))
    }

    @Suspendable
    private fun <T : SessionMessage> sendAndReceiveInternal(session: ProtocolSession, message: SessionMessage, receiveType: Class<T>): T {
        return suspendAndExpectReceive(SendAndReceive(session, message, receiveType))
    }

    @Suspendable
    private fun getSession(otherParty: Party, sessionProtocol: ProtocolLogic<*>): ProtocolSession {
        return openSessions[Pair(sessionProtocol, otherParty)] ?: startNewSession(otherParty, sessionProtocol)
    }

    @Suspendable
    private fun startNewSession(otherParty: Party, sessionProtocol: ProtocolLogic<*>) : ProtocolSession {
        val session = ProtocolSession(sessionProtocol, otherParty, random63BitValue(), null)
        openSessions[Pair(sessionProtocol, otherParty)] = session
        val counterpartyProtocol = sessionProtocol.getCounterpartyMarker(otherParty).name
        val sessionInit = SessionInit(session.ourSessionId, serviceHub.storageService.myLegalIdentity, counterpartyProtocol)
        val sessionInitResponse = sendAndReceiveInternal(session, sessionInit, SessionInitResponse::class.java)
        if (sessionInitResponse is SessionConfirm) {
            session.otherPartySessionId = sessionInitResponse.initiatedSessionId
            return session
        } else {
            sessionInitResponse as SessionReject
            throw ProtocolSessionException("Party $otherParty rejected session attempt: ${sessionInitResponse.errorMessage}")
        }
    }

    @Suspendable
    private fun <T : SessionMessage> suspendAndExpectReceive(receiveRequest: ReceiveRequest<T>): T {
        fun getReceivedMessage(): ExistingSessionMessage? = receiveRequest.session.receivedMessages.poll()

        val receivedMessage = getReceivedMessage() ?: run {
            // Suspend while we wait for the receive
            receiveRequest.session.waitingForResponse = true
            suspend(receiveRequest)
            receiveRequest.session.waitingForResponse = false
            getReceivedMessage()
                    ?: throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but got nothing: $id $receiveRequest")
        }

        if (receivedMessage is SessionEnd) {
            openSessions.values.remove(receiveRequest.session)
            throw ProtocolSessionException("Counterparty on ${receiveRequest.session.otherParty} has prematurly ended")
        } else if (receiveRequest.receiveType.isInstance(receivedMessage)) {
            return receiveRequest.receiveType.cast(receivedMessage)
        } else {
            throw IllegalStateException("Was expecting a ${receiveRequest.receiveType.simpleName} but got $receivedMessage: $id $receiveRequest")
        }
    }

    @Suspendable
    private fun suspend(ioRequest: ProtocolIORequest) {
        commitTransaction()
        parkAndSerialize { fiber, serializer ->
            logger.trace { "Suspended $id on $ioRequest" }
            try {
                actionOnSuspend(ioRequest)
            } catch (t: Throwable) {
                // Do not throw exception again - Quasar completely bins it.
                logger.warn("Captured exception which was swallowed by Quasar", t)
                // TODO When error handling is introduced, look into whether we should be deleting the checkpoint and
                // completing the Future
                processException(t)
            }
        }
        createTransaction()
    }

    private fun processException(t: Throwable) {
        actionOnEnd()
        _resultFuture?.setException(t)
    }

    internal fun resume(scheduler: FiberScheduler) {
        try {
            if (fromCheckpoint) {
                logger.info("$id resumed from checkpoint")
                fromCheckpoint = false
                Fiber.unparkDeserialized(this, scheduler)
            } else if (state == State.NEW) {
                logger.trace { "$id started" }
                start()
            } else {
                logger.trace { "$id resumed" }
                Fiber.unpark(this, QUASAR_UNBLOCKER)
            }
        } catch (t: Throwable) {
            logger.error("$id threw '${t.rootCause}'")
            logger.trace {
                val s = StringWriter()
                t.rootCause.printStackTrace(PrintWriter(s))
                "Stack trace of protocol error: $s"
            }
        }
    }

}
