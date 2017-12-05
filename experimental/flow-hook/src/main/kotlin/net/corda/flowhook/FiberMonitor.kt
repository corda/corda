package net.corda.flowhook

import co.paralleluniverse.fibers.Fiber
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * This is a debugging helper class that dumps the map of Fiber->DB connection, or more precisely, the
 * Fiber->(DB tx -> DB connection) map, as there may be multiple transactions per fiber.
 */

data class MonitorEvent(val type: MonitorEventType, val keys: List<Any>, val extra: Any? = null)

data class FullMonitorEvent(val timestamp: Instant, val trace: List<StackTraceElement>, val event: MonitorEvent) {
    override fun toString() = event.toString()
}

enum class MonitorEventType {
    TransactionCreated,
    ConnectionRequested,
    ConnectionAcquired,
    ConnectionReleased,

    FiberStarted,
    FiberParking,
    FiberException,
    FiberResumed,
    FiberEnded,

    ExecuteTransition
}

object FiberMonitor {
    private val log = contextLogger()
    private val jobQueue = LinkedBlockingQueue<Job>()
    private val started = AtomicBoolean(false)
    private var trackerThread: Thread? = null

    val correlator = MonitorEventCorrelator()

    sealed class Job {
        data class NewEvent(val event: FullMonitorEvent) : Job()
        object Finish : Job()
    }

    fun newEvent(event: MonitorEvent) {
        if (trackerThread != null) {
            jobQueue.add(Job.NewEvent(FullMonitorEvent(Instant.now(), Exception().stackTrace.toList(), event)))
        }
    }

    fun start() {
        if (started.compareAndSet(false, true)) {
            require(trackerThread == null)
            trackerThread = thread(name = "Fiber monitor", isDaemon = true) {
                while (true) {
                    val job = jobQueue.poll(1, TimeUnit.SECONDS)
                    when (job) {
                        is Job.NewEvent -> processEvent(job)
                        Job.Finish -> return@thread
                    }
                }
            }
        }
    }

    private fun processEvent(job: Job.NewEvent) {
        correlator.addEvent(job.event)
        checkLeakedTransactions(job.event.event)
        checkLeakedConnections(job.event.event)
    }

    inline fun <reified R, A : Any> R.getField(name: String): A {
        val field = R::class.java.getDeclaredField(name)
        field.isAccessible = true
        return uncheckedCast(field.get(this))
    }

    fun <A : Any> Any.getFieldFromObject(name: String): A {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return uncheckedCast(field.get(this))
    }

    fun getThreadLocalMapEntryValues(locals: Any): List<Any> {
        val table: Array<Any?> = locals.getFieldFromObject("table")
        return table.mapNotNull { it?.getFieldFromObject<Any>("value") }
    }

    fun getStashedThreadLocals(fiber: Fiber<*>): List<Any> {
        val fiberLocals: Any = fiber.getField("fiberLocals")
        val inheritableFiberLocals: Any = fiber.getField("inheritableFiberLocals")
        return getThreadLocalMapEntryValues(fiberLocals) + getThreadLocalMapEntryValues(inheritableFiberLocals)
    }

    fun getTransactionStack(transaction: DatabaseTransaction): List<DatabaseTransaction> {
        val transactions = ArrayList<DatabaseTransaction>()
        var currentTransaction: DatabaseTransaction? = transaction
        while (currentTransaction != null) {
            transactions.add(currentTransaction)
            currentTransaction = currentTransaction.outerTransaction
        }
        return transactions
    }

    private fun checkLeakedTransactions(event: MonitorEvent) {
        if (event.type == MonitorEventType.FiberParking) {
            val fiber = event.keys.mapNotNull { it as? Fiber<*> }.first()
            val threadLocals = getStashedThreadLocals(fiber)
            val transactions = threadLocals.mapNotNull { it as? DatabaseTransaction }.flatMap { getTransactionStack(it) }
            val leakedTransactions = transactions.filter { it.connectionCreated && !it.connection.isClosed }
            if (leakedTransactions.isNotEmpty()) {
                log.warn("Leaked open database transactions on yield $leakedTransactions")
            }
        }
    }

    private fun checkLeakedConnections(event: MonitorEvent) {
        if (event.type == MonitorEventType.FiberParking) {
            val events = correlator.events[event.keys[0]]!!
            val acquiredConnections = events.mapNotNullTo(HashSet()) {
                if (it.event.type == MonitorEventType.ConnectionAcquired) {
                    it.event.keys.mapNotNull { it as? Connection }.first()
                } else {
                    null
                }
            }
            val releasedConnections = events.mapNotNullTo(HashSet()) {
                if (it.event.type == MonitorEventType.ConnectionReleased) {
                    it.event.keys.mapNotNull { it as? Connection }.first()
                } else {
                    null
                }
            }
            val leakedConnections = (acquiredConnections - releasedConnections).filter { !it.isClosed }
            if (leakedConnections.isNotEmpty()) {
                log.warn("Leaked open connections $leakedConnections")
            }
        }
    }
}

class MonitorEventCorrelator {
    private val _events = HashMap<Any, ArrayList<FullMonitorEvent>>()
    val events: Map<Any, ArrayList<FullMonitorEvent>> get() = _events

    fun getUnique() = events.values.toSet().associateBy { it.flatMap { it.event.keys }.toSet() }

    fun getByType() = events.entries.groupBy { it.key.javaClass }

    fun addEvent(fullMonitorEvent: FullMonitorEvent) {
        val list = link(fullMonitorEvent.event.keys)
        list.add(fullMonitorEvent)
        for (key in fullMonitorEvent.event.keys) {
            _events[key] = list
        }
    }

    fun link(keys: List<Any>): ArrayList<FullMonitorEvent> {
        val eventLists = HashSet<ArrayList<FullMonitorEvent>>()
        for (key in keys) {
            val list = _events[key]
            if (list != null) {
                eventLists.add(list)
            }
        }
        return when {
            eventLists.isEmpty() -> ArrayList()
            eventLists.size == 1 -> eventLists.first()
            else -> mergeAll(eventLists)
        }
    }

    fun mergeAll(lists: Collection<List<FullMonitorEvent>>): ArrayList<FullMonitorEvent> {
        return lists.fold(ArrayList()) { merged, next -> merge(merged, next) }
    }

    fun merge(a: List<FullMonitorEvent>, b: List<FullMonitorEvent>): ArrayList<FullMonitorEvent> {
        val merged = ArrayList<FullMonitorEvent>()
        var aIndex = 0
        var bIndex = 0
        while (true) {
            if (aIndex >= a.size) {
                merged.addAll(b.subList(bIndex, b.size))
                return merged
            }
            if (bIndex >= b.size) {
                merged.addAll(a.subList(aIndex, a.size))
                return merged
            }
            val aElem = a[aIndex]
            val bElem = b[bIndex]
            if (aElem.timestamp < bElem.timestamp) {
                merged.add(aElem)
                aIndex++
            } else {
                merged.add(bElem)
                bIndex++
            }
        }
    }
}