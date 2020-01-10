package net.corda.nodeapi.internal.lifecycle

import net.corda.core.node.services.CordaServiceCriticalFailureException
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.util.Collections.singleton
import java.util.LinkedList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.system.exitProcess

/**
 * Responsible for distributing of various `NodeLifecycleEvent` to `NodeLifecycleObserver`.
 *
 * This class may do it in an asynchronous fashion. Also it might listen to the feedback from observers on the notifications sent and perform
 * actions depending on the observer's priority.
 *
 * The class is safe for concurrent use from multiple threads.
 */
class NodeLifecycleEventsDistributor {

    companion object {
        private val log = contextLogger()

        private val crtiticalEventsClasses: Set<Class<out NodeLifecycleEvent>> = setOf(
                NodeLifecycleEvent.BeforeStart::class.java,
                NodeLifecycleEvent.AfterStart::class.java)
        private val criticalExceptionsClasses: Set<Class<out Throwable>> = setOf(CordaServiceCriticalFailureException::class.java)
    }

    /**
     * Order is maintained by priority and within equal priority by full class name.
     */
    private val prioritizedObservers: MutableList<NodeLifecycleObserver> = mutableListOf()

    private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()

    /**
     * Adds observer to the distribution list.
     */
    fun <T : NodeLifecycleObserver> add(observer: T) : T {
        addAll(singleton(observer))
        return observer
    }

    /**
     * Adds multiple observers to the distribution list.
     */
    fun <T : NodeLifecycleObserver> addAll(observers: Collection<T>) : Collection<T> {

        data class SortingKey(val priority: Int, val clazz: Class<*>) : Comparable<SortingKey> {
            override fun compareTo(other: SortingKey): Int {
                return if(priority != other.priority) {
                    priority - other.priority
                } else {
                    clazz.name.compareTo(other.clazz.name)
                }
            }
        }

        readWriteLock.writeLock().executeLocked {
            prioritizedObservers.addAll(observers)
            // In-place sorting
            prioritizedObservers.sortBy { SortingKey(it.priority, it.javaClass) }
        }

        return observers
    }

    /**
     * Distributes event to all the observers previously added
     */
    fun distributeEvent(event: NodeLifecycleEvent) {
        val snapshot = readWriteLock.readLock().executeLocked { LinkedList(prioritizedObservers) }
        val orderedSnapshot = if (event.reversedPriority) snapshot.reversed() else snapshot

        val updateResult = orderedSnapshot.map { it.update(event) }

        val (updatedOK, updateFailed) = updateResult.partition { it.isSuccess }

        if(updateFailed.isNotEmpty()) {
            log.error("Failed to distribute event $event, failure outcome: $updateFailed")
            if(event.javaClass in crtiticalEventsClasses) {
                handlePossibleFatalTermination(event, updateFailed.map { it as Try.Failure<String> })
            }
        }

        log.debug { "Event $event distribution outcome: $updatedOK" }
    }

    private fun handlePossibleFatalTermination(event: NodeLifecycleEvent, updateFailed: List<Try.Failure<String>>) {
        val criticalFailures = updateFailed.filter { it.exception.javaClass in criticalExceptionsClasses }
        if(criticalFailures.isNotEmpty()) {
            log.error("During processing of $event critical failures been reported: $criticalFailures. JVM will be terminated.")
            exitProcess(1)
        }
    }

    private fun <T> Lock.executeLocked(block: () -> T) : T {
        try {
            lockInterruptibly()
            return block()
        } finally {
            unlock()
        }
    }
}