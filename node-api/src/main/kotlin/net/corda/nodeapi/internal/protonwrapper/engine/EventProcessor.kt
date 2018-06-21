/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.protonwrapper.engine

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.impl.ReceivedMessageImpl
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import org.apache.qpid.proton.Proton
import org.apache.qpid.proton.amqp.messaging.Accepted
import org.apache.qpid.proton.amqp.messaging.Rejected
import org.apache.qpid.proton.amqp.transport.DeliveryState
import org.apache.qpid.proton.amqp.transport.ErrorCondition
import org.apache.qpid.proton.engine.*
import org.apache.qpid.proton.engine.impl.CollectorImpl
import org.apache.qpid.proton.reactor.FlowController
import org.apache.qpid.proton.reactor.Handshaker
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The EventProcessor class converts calls on the netty scheduler/pipeline
 * into proton-j engine event calls into the ConnectionStateMachine.
 * It also registers a couple of standard event processors for the basic connection handshake
 * and simple sliding window flow control, so that these events don't have to live inside ConnectionStateMachine.
 * Everything here is single threaded, because the proton-j library has to be run that way.
 */
internal class EventProcessor(channel: Channel,
                              serverMode: Boolean,
                              localLegalName: String,
                              remoteLegalName: String,
                              userName: String?,
                              password: String?) : BaseHandler() {
    companion object {
        private const val FLOW_WINDOW_SIZE = 10
    }

    private val log = LoggerFactory.getLogger(localLegalName)
    private val lock = ReentrantLock()
    private var pendingExecute: Boolean = false
    private val executor: ScheduledExecutorService = channel.eventLoop()
    private val collector = Proton.collector() as CollectorImpl
    private val handlers = mutableListOf<Handler>()
    private val stateMachine: ConnectionStateMachine = ConnectionStateMachine(serverMode,
            collector,
            localLegalName,
            remoteLegalName,
            userName,
            password)

    val connection: Connection = stateMachine.connection

    init {
        addHandler(Handshaker())
        addHandler(FlowController(FLOW_WINDOW_SIZE))
        addHandler(stateMachine)
        connection.context = channel
        tick(stateMachine.connection)
    }

    fun addHandler(handler: Handler) = handlers.add(handler)

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
            try {
                if ((connection.localState != EndpointState.CLOSED) && !connection.transport.isClosed) {
                    val now = System.currentTimeMillis()
                    val tickDelay = Math.max(0L, connection.transport.tick(now) - now)
                    executor.schedule({
                        tick(connection)
                        processEvents()
                    }, tickDelay, TimeUnit.MILLISECONDS)
                }
            } catch (ex: Exception) {
                connection.transport.close()
                connection.condition = ErrorCondition()
            }
        }
    }

    fun processEvents() {
        lock.withLock {
            pendingExecute = false
            log.debug { "Process Events" }
            while (true) {
                val ev = popEvent() ?: break
                log.debug { "Process event: $ev" }
                for (handler in handlers) {
                    handler.handle(ev)
                }
            }
            stateMachine.processTransport()
            log.debug { "Process Events Done" }
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
        if (connection.localState != EndpointState.CLOSED) {
            connection.close()
            processEvents()
            connection.free()
            processEvents()
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