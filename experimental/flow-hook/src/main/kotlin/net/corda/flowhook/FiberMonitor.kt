package net.corda.flowhook

import co.paralleluniverse.fibers.Fiber
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    SmExecuteTransition,
    SmScheduleEvent,

    NettyThreadLocalMapCreated,

    SetThreadLocals,
    SetInheritableThreadLocals,
    GetThreadLocals,
    GetInheritableThreadLocals
}

/**
 * This is a monitor processing events coming from [FlowHookContainer]. It just appends them to a log that allows
 * analysis of the events.
 *
 * Suggested way of debugging using this class and IntelliJ:
 * 1. Hook the function calls you're interested in using [FlowHookContainer].
 * 2. Add an associated event type in [MonitorEventType].
 * 3. Call [newEvent] in the hook. Provide some keys to allow analysis. Example keys are the current fiber ID or a
 *   specific DB transaction. You can also provide additional info about the event using [MonitorEvent.extra].
 * 4. Run your test and break on [newEvent] or [inspect].
 * 5. Inspect the [correlator] in the debugger. E.g. you can add a watch for [MonitorEventCorrelator.getByType].
 *   You can search for specific objects by using filter expressions in the debugger.
 */
object FiberMonitor {
    private val log = loggerFor<FiberMonitor>()
    private val started = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    val correlator = MonitorEventCorrelator()

    fun newEvent(event: MonitorEvent) {
        if (executor != null) {
            val fullEvent = FullMonitorEvent(Instant.now(), Exception().stackTrace.toList(), event)
            executor!!.execute {
                processEvent(fullEvent)
            }
        }
    }

    fun start() {
        if (started.compareAndSet(false, true)) {
            require(executor == null)
            executor = Executors.newSingleThreadScheduledExecutor()
            executor!!.scheduleAtFixedRate(this::inspect, 100, 100, TimeUnit.MILLISECONDS)
        }
    }

    // Break on this function or [newEvent].
    private fun inspect() {
    }

    private fun processEvent(event: FullMonitorEvent) {
        correlator.addEvent(event)
        checkLeakedTransactions(event.event)
        checkLeakedConnections(event.event)
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
            val events = correlator.merged()[event.keys[0]]!!
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

/**
 * This class holds the event log.
 *
 * Each event has a list of key associated with it. "Relatedness" is then the transitive closure of two events sharing a key.
 *
 * [merged] returns a map from key to related events. Note that an eventlist may be associated by several keys.
 * [getUnique] makes these lists unique by keying on the set of keys associated with the events.
 * [getByType] simply groups by the type of the keys. This is probably the most useful "top-level" breakdown of events.
 */
class MonitorEventCorrelator {
    private val events = ArrayList<FullMonitorEvent>()

    fun getUnique() = merged().values.toSet().associateBy { it.flatMap { it.event.keys }.toSet() }

    fun getByType() = merged().entries.groupBy { it.key.javaClass }

    fun addEvent(fullMonitorEvent: FullMonitorEvent) {
        events.add(fullMonitorEvent)
    }

    fun merged(): Map<Any, List<FullMonitorEvent>> {
        val merged = HashMap<Any, ArrayList<FullMonitorEvent>>()
        for (event in events) {
            val eventLists = HashSet<ArrayList<FullMonitorEvent>>()
            for (key in event.event.keys) {
                val list = merged[key]
                if (list != null) {
                    eventLists.add(list)
                }
            }
            val newList = when (eventLists.size) {
                0 -> ArrayList()
                1 -> eventLists.first()
                else -> mergeAll(eventLists)
            }
            newList.add(event)
            for (key in event.event.keys) {
                merged[key] = newList
            }
        }
        return merged
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