package net.corda.flowhook

import co.paralleluniverse.fibers.Fiber
import net.corda.core.internal.declaredField
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.InitialSessionMessage
import net.corda.node.services.statemachine.SessionMessage
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer
import java.sql.Connection
import java.util.concurrent.TimeUnit

@Suppress("UNUSED", "UNUSED_PARAMETER")
object FlowHookContainer {

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber")
    fun park1(blocker: Any?, postParkAction: Any?, timeout: Long?, unit: TimeUnit?) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberParking, keys = listOf(Fiber.currentFiber())))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber", passThis = true)
    fun exec(fiber: Any) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberResuming, keys = listOf(fiber)))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber", passThis = true)
    fun onParked(fiber: Any) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberParked, keys = listOf(fiber)))
    }

    @JvmStatic
    @Hook("net.corda.node.services.statemachine.FlowStateMachineImpl")
    fun run() {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberStarted, keys = listOf(Fiber.currentFiber())))
    }

    @JvmStatic
    @Hook("net.corda.node.services.statemachine.FlowStateMachineImpl", passThis = true)
    fun scheduleEvent(fiber: Any, event: Event) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.SmScheduleEvent, keys = listOf(fiber), extra = listOf(event, currentFiberOrThread())))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber")
    fun onCompleted() {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberEnded, keys = listOf(Fiber.currentFiber())))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber")
    fun onException(exception: Throwable) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberException, keys = listOf(Fiber.currentFiber()), extra = exception))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber")
    fun onResumed() {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberResumed, keys = listOf(Fiber.currentFiber())))
    }

    @JvmStatic
    @Hook("net.corda.nodeapi.internal.persistence.DatabaseTransaction", passThis = true, position = HookPosition.After)
    fun DatabaseTransaction(
            transaction: Any,
            isolation: Int,
            threadLocal: Any,
            transactionBoundaries: Any,
            cordaPersistence: Any
    ) {
        val keys = ArrayList<Any>().apply {
            add(transaction)
            Fiber.currentFiber()?.let { add(it) }
        }
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.TransactionCreated, keys = keys))
    }

    @JvmStatic
    @Hook("io.netty.util.internal.InternalThreadLocalMap", passThis = true, position = HookPosition.After)
    fun InternalThreadLocalMap(
            internalThreadLocalMap: Any
    ) {
        val keys = listOf(
                internalThreadLocalMap,
                currentFiberOrThread()
        )
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.NettyThreadLocalMapCreated, keys = keys))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.concurrent.util.ThreadAccess")
    fun setThreadLocals(thread: Thread, threadLocals: Any?) {
        FiberMonitor.newEvent(MonitorEvent(
                MonitorEventType.SetThreadLocals,
                keys = listOf(currentFiberOrThread()),
                extra = threadLocals?.let { FiberMonitor.getThreadLocalMapEntryValues(it) }
        ))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.concurrent.util.ThreadAccess")
    fun setInheritableThreadLocals(thread: Thread, threadLocals: Any?) {
        FiberMonitor.newEvent(MonitorEvent(
                MonitorEventType.SetInheritableThreadLocals,
                keys = listOf(currentFiberOrThread()),
                extra = threadLocals?.let { FiberMonitor.getThreadLocalMapEntryValues(it) }
        ))
    }

    @JvmStatic
    @Hook("co.paralleluniverse.concurrent.util.ThreadAccess")
    fun getThreadLocals(thread: Thread): (threadLocals: Any?) -> Unit {
        return { threadLocals ->
            FiberMonitor.newEvent(MonitorEvent(
                    MonitorEventType.GetThreadLocals,
                    keys = listOf(currentFiberOrThread()),
                    extra = threadLocals?.let { FiberMonitor.getThreadLocalMapEntryValues(it) }
            ))
        }
    }

    @JvmStatic
    @Hook("co.paralleluniverse.concurrent.util.ThreadAccess")
    fun getInheritableThreadLocals(thread: Thread): (threadLocals: Any?) -> Unit {
        return { threadLocals ->
            FiberMonitor.newEvent(MonitorEvent(
                    MonitorEventType.GetInheritableThreadLocals,
                    keys = listOf(currentFiberOrThread()),
                    extra = threadLocals?.let { FiberMonitor.getThreadLocalMapEntryValues(it) }
            ))
        }
    }

    @JvmStatic
    @Hook("com.zaxxer.hikari.HikariDataSource")
    fun getConnection(): (Any) -> Unit {
        val transactionOrThread = currentTransactionOrThread()
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.ConnectionRequested, keys = listOf(transactionOrThread)))
        return { connection ->
            FiberMonitor.newEvent(MonitorEvent(MonitorEventType.ConnectionAcquired, keys = listOf(transactionOrThread, connection)))
        }
    }

    @JvmStatic
    @Hook("com.zaxxer.hikari.pool.ProxyConnection", passThis = true, position = HookPosition.After)
    fun close(connection: Any) {
        connection as Connection
        val transactionOrThread = currentTransactionOrThread()
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.ConnectionReleased, keys = listOf(transactionOrThread, connection)))
    }

    @JvmStatic
    @Hook("net.corda.node.services.statemachine.TransitionExecutorImpl")
    fun executeTransition(
            fiber: Any,
            previousState: Any,
            event: Any,
            transition: Any,
            actionExecutor: Any
    ) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.SmExecuteTransition, keys = listOf(fiber), extra = object {
            val previousState = previousState
            val event = event
            val transition = transition
        }))
    }

    @JvmStatic
    @Hook("net.corda.node.services.statemachine.FlowMessagingImpl")
    fun sendSessionMessage(party: Any, message: Any, deduplicationId: Any) {
        message as SessionMessage
        val sessionId = when (message) {
            is InitialSessionMessage -> {
                message.initiatorSessionId
            }
            is ExistingSessionMessage -> {
                message.recipientSessionId
            }
        }
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.SendSessionMessage, keys = listOf(currentFiberOrThread(), sessionId)))
    }

    @JvmStatic
    @Hook("org.apache.activemq.artemis.core.io.buffer.TimedBuffer", passThis = true)
    fun flush(buffer: Any, force: Boolean): () -> Unit {
        buffer as TimedBuffer
        val thread = Thread.currentThread()
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.BrokerFlushStart, keys = listOf(thread), extra = object {
            val force = force
            val pendingSync = buffer.declaredField<Boolean>("pendingSync").value
        }))

        return {
            FiberMonitor.newEvent(MonitorEvent(MonitorEventType.BrokerFlushEnd, keys = listOf(thread)))
        }
    }

    private fun currentFiberOrThread(): Any {
        return Fiber.currentFiber() ?: Thread.currentThread()
    }

    private fun currentTransactionOrThread(): Any {
        return try {
            contextTransactionOrNull
        } catch (exception: IllegalStateException) {
            null
        } ?: Thread.currentThread()
    }
}
