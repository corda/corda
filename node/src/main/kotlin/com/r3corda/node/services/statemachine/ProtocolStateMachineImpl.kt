package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolStateMachine
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.ServiceHubInternal
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
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
class ProtocolStateMachineImpl<R>(val logic: ProtocolLogic<R>,
                                  scheduler: FiberScheduler,
                                  private val loggerName: String)
: Fiber<R>("protocol", scheduler), ProtocolStateMachine<R> {

    // These fields shouldn't be serialised, so they are marked @Transient.
    @Transient lateinit override var serviceHub: ServiceHubInternal
    @Transient internal lateinit var suspendAction: (ProtocolIORequest) -> Unit
    @Transient internal lateinit var actionOnEnd: () -> Unit
    @Transient internal var receivedPayload: Any? = null

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
    /**
     * Unique ID for the deserialized instance protocol state machine. This is NOT maintained across a state machine
     * being serialized and then deserialized.
     */
    override val machineId: Long get() = this.id

    init {
        logic.psm = this
    }

    @Suspendable @Suppress("UNCHECKED_CAST")
    override fun run(): R {
        createTransaction()
        val result = try {
            logic.call()
        } catch (t: Throwable) {
            actionOnEnd()
            _resultFuture?.setException(t)
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
        TransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
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
    private fun <T : Any> suspendAndExpectReceive(receiveRequest: ReceiveRequest<T>): UntrustworthyData<T> {
        suspend(receiveRequest)
        check(receivedPayload != null) { "Expected to receive something" }
        val untrustworthy = UntrustworthyData(receiveRequest.receiveType.cast(receivedPayload))
        receivedPayload = null
        return untrustworthy
    }

    @Suspendable
    override fun <T : Any> sendAndReceive(topic: String,
                                          destination: Party,
                                          sessionIDForSend: Long,
                                          sessionIDForReceive: Long,
                                          payload: Any,
                                          receiveType: Class<T>): UntrustworthyData<T> {
        return suspendAndExpectReceive(SendAndReceive(topic, destination, payload, sessionIDForSend, UUID.randomUUID(), receiveType, sessionIDForReceive))
    }

    @Suspendable
    override fun <T : Any> receive(topic: String, sessionIDForReceive: Long, receiveType: Class<T>): UntrustworthyData<T> {
        return suspendAndExpectReceive(ReceiveOnly(topic, receiveType, sessionIDForReceive))
    }

    @Suspendable
    override fun send(topic: String, destination: Party, sessionID: Long, payload: Any) {
        suspend(SendOnly(destination, topic, payload, sessionID, UUID.randomUUID()))
    }

    @Suspendable
    private fun suspend(protocolIORequest: ProtocolIORequest) {
        commitTransaction()
        parkAndSerialize { fiber, serializer ->
            try {
                suspendAction(protocolIORequest)
            } catch (t: Throwable) {
                // Do not throw exception again - Quasar completely bins it.
                logger.warn("Captured exception which was swallowed by Quasar", t)
                actionOnEnd()
                _resultFuture?.setException(t)
            }
        }
        createTransaction()
    }

}
