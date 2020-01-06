package net.corda.ext.api.lifecycle

import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.util.Collections.singleton
import java.util.LinkedList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

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
        val orderedSnapshot = if (event.shutdown) snapshot.reversed() else snapshot

        val updateResult = orderedSnapshot.map { it.update(event) }

        val (updatedOK, updateFailed) = updateResult.partition { it.isSuccess }

        if(updateFailed.isNotEmpty()) {
            log.error("Failed to distribute event $event, failure outcome: $updateFailed")
        }

        log.debug { "Event $event distribution outcome: $updatedOK" }

        // TODO: Should we do this asynchronously but maintaining priority order?
        // TODO: How shall we handle failed distribution events for different types and for different priorities?
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