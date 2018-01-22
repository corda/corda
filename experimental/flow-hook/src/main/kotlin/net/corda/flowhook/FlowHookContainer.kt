package net.corda.flowhook

import co.paralleluniverse.fibers.Fiber
import net.corda.node.services.statemachine.Event
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import java.sql.Connection

@Suppress("UNUSED")
object FlowHookContainer {

    @JvmStatic
    @Hook("co.paralleluniverse.fibers.Fiber")
    fun park() {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.FiberParking, keys = listOf(Fiber.currentFiber())))
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
