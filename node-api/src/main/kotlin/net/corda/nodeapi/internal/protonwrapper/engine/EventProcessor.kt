package net.corda.nodeapi.internal.protonwrapper.engine

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import net.corda.core.internal.declaredField
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.impl.ReceivedMessageImpl
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import org.apache.qpid.proton.Proton
import org.apache.qpid.proton.amqp.messaging.Accepted
import org.apache.qpid.proton.amqp.messaging.Rejected
import org.apache.qpid.proton.amqp.transport.DeliveryState
import org.apache.qpid.proton.amqp.transport.ErrorCondition
import org.apache.qpid.proton.engine.*
import org.apache.qpid.proton.reactor.FlowController
import org.apache.qpid.proton.reactor.Handshaker
import org.slf4j.MDC
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * The EventProcessor class converts calls on the netty scheduler/pipeline
 * into proton-j engine event calls into the ConnectionStateMachine.
 * It also registers a couple of standard event processors for the basic connection handshake
 * and simple sliding window flow control, so that these events don't have to live inside ConnectionStateMachine.
 * Everything here is single threaded, because the proton-j library has to be run that way.
 */
internal class EventProcessor(private val channel: Channel,
                              private val serverMode: Boolean,
                              private val localLegalName: String,
                              private val remoteLegalName: String,
                              userName: String?,
                              password: String?) {
    companion object {
        private const val CORDA_AMQP_FLOW_WINDOW_SIZE_PROP_NAME = "net.corda.nodeapi.eventprocessor.FlowWindowSize"

        private val FLOW_WINDOW_SIZE = Integer.getInteger(CORDA_AMQP_FLOW_WINDOW_SIZE_PROP_NAME, 5)
        private val log = contextLogger()
    }

    private fun withMDC(block: () -> Unit) {
        val oldMDC = MDC.getCopyOfContextMap() ?: emptyMap<String, String>()
        try {
            MDC.put("serverMode", serverMode.toString())
            MDC.put("localLegalName", localLegalName)
            MDC.put("localAddress", channel.localAddress()?.toString())
            MDC.put("remoteLegalName", remoteLegalName)
            MDC.put("remoteAddress", channel.remoteAddress()?.toString())
            block()
        } finally {
            MDC.setContextMap(oldMDC)
        }
    }

    private fun logDebugWithMDC(msg: () -> String) {
        if (log.isDebugEnabled) {
            withMDC { log.debug(msg()) }
        }
    }

    private val lock = ReentrantLock()
    @Volatile
    private var pendingExecute: Boolean = false
    @Volatile
    private var processorClosed: Boolean = false
    private val executor: ScheduledExecutorService = channel.eventLoop()
    private val collector = Proton.collector()
    private val handlers: List<Handler>
    private val stateMachine: ConnectionStateMachine = ConnectionStateMachine(serverMode,
            collector,
            localLegalName,
            remoteLegalName,
            userName,
            password)

    val connection: Connection = stateMachine.connection

    init {
        handlers = listOf(Handshaker(), FlowController(FLOW_WINDOW_SIZE), stateMachine)
        connection.context = channel
        tick(stateMachine.connection)
    }

    private fun popEvent(): Event? {
        var ev = collector.peek()
        if (ev != null) {
            ev = ev.copy() // prevent mutation by collector.pop()
            collector.pop()
        }
        return ev
    }

    private fun tick(connection: Connection) {
        lock.withLock {
            logDebugWithMDC { "Tick" }
            try {
                if ((connection.localState != EndpointState.CLOSED) && !connection.transport.isClosed) {
                    val now = System.currentTimeMillis()
                    val tickDelay = max(0L, connection.transport.tick(now) - now)
                    executor.schedule({
                        tick(connection)
                        processEvents()
                    }, tickDelay, TimeUnit.MILLISECONDS)
                    logDebugWithMDC {"Tick done. Next tick scheduled in $tickDelay ms"}
                } else {
                    logDebugWithMDC { "Connection closed - no more ticking" }
                }
            } catch (ex: Exception) {
                withMDC { log.info("Tick failed", ex) }
                connection.transport.close()
                connection.condition = ErrorCondition()
            }
        }
    }

    private fun processEvents() {
        lock.withLock {
            pendingExecute = false
            logDebugWithMDC { "Process Events" }
            while (true) {
                val ev = popEvent() ?: break
                logDebugWithMDC { "Process event: $ev" }
                for (handler in handlers) {
                    handler.handle(ev)
                }
            }
            stateMachine.processTransport()
            logDebugWithMDC { "Process Events Done" }
        }
    }

    fun processEventsAsync() {
        lock.withLock {
            if (!pendingExecute) {
                pendingExecute = true
                executor.execute { processEvents() }
            }
        }
    }

    fun close() {
        lock.withLock {
            if (!processorClosed) {
                processorClosed = true
                connection.logLocalState("Before close")
                connection.close()
                processEvents()
                logDebugWithMDC { "Freeing-up connection" }
                connection.free()
                processEvents()
                connection.logLocalState("After close")
            } else {
                logDebugWithMDC { "Processor is already closed" }
            }
        }
    }

    private fun Connection.logLocalState(prefix: String) {
        if (log.isDebugEnabled) {
            val freedTry = Try.on { declaredField<Boolean>("freed").value }
            val refcountTry = Try.on { declaredField<Int>("refcount").value }
            logDebugWithMDC { "$prefix, local state: $localState, freed: $freedTry, refcount: $refcountTry" }
        }
    }

    fun transportProcessInput(msg: ByteBuf) = lock.withLock { stateMachine.transportProcessInput(msg) }

    fun transportProcessOutput(ctx: ChannelHandlerContext) = lock.withLock { stateMachine.transportProcessOutput(ctx) }

    fun transportWriteMessage(msg: SendableMessageImpl) = lock.withLock { stateMachine.transportWriteMessage(msg) }

    fun complete(completer: ReceivedMessageImpl.MessageCompleter) = lock.withLock {
        val status: DeliveryState = if (completer.status == MessageStatus.Acknowledged) Accepted.getInstance() else Rejected()
        completer.delivery.disposition(status)
        completer.delivery.settle()
    }
}