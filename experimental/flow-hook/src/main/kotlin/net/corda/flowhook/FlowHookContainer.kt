package net.corda.flowhook

import co.paralleluniverse.fibers.Fiber
import net.corda.node.services.statemachine.ActionExecutor
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.transitions.TransitionResult
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransactionManager
import rx.subjects.Subject
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
    @Hook("net.corda.node.utilities.DatabaseTransaction", passThis = true, position = HookPosition.After)
    fun DatabaseTransaction(
            transaction: Any,
            isolation: Int,
            threadLocal: ThreadLocal<*>,
            transactionBoundaries: Subject<*, *>,
            cordaPersistence: CordaPersistence
    ) {
        val keys = ArrayList<Any>().apply {
            add(transaction)
            Fiber.currentFiber()?.let { add(it) }
        }
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.TransactionCreated, keys = keys))
    }

    @JvmStatic
    @Hook("com.zaxxer.hikari.HikariDataSource")
    fun getConnection(): (Connection) -> Unit {
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
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ) {
        FiberMonitor.newEvent(MonitorEvent(MonitorEventType.ExecuteTransition, keys = listOf(fiber), extra = object {
            val previousState = previousState
            val event = event
            val transition = transition
        }))
    }

    private fun currentTransactionOrThread(): Any {
        return try {
            DatabaseTransactionManager.currentOrNull()
        } catch (exception: IllegalStateException) {
            null
        } ?: Thread.currentThread()
    }
}
