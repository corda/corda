package net.corda.nodeapi.internal.lifecycle

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.core.node.services.CordaServiceCriticalFailureException
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import java.util.Collections.singleton
import java.util.LinkedList
import java.util.concurrent.Executors
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

        private val criticalEventsClasses: Set<Class<out NodeLifecycleEvent>> = setOf(
                NodeLifecycleEvent.BeforeNodeStart::class.java,
                NodeLifecycleEvent.AfterNodeStart::class.java)
        private val criticalExceptionsClasses: Set<Class<out Throwable>> = setOf(CordaServiceCriticalFailureException::class.java)
    }

    /**
     * Order is maintained by priority and within equal priority by full class name.
     */
    private val prioritizedObservers: MutableList<NodeLifecycleObserver> = mutableListOf()

    private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()

    private val executor = Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("NodeLifecycleEventsDistributor-%d").build())

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
                if(priority != other.priority) {
                    return priority - other.priority
                }
                return clazz.name.compareTo(other.clazz.name)
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

        //executor.execute  {
            val orderedSnapshot = if (event.reversedPriority) snapshot.reversed() else snapshot
            orderedSnapshot.forEach {
                log.info("Distributing event $event to: $it")
                val updateResult = it.update(event)
                if (updateResult.isSuccess) {
                    log.info("Event $event distribution outcome: $updateResult")
                } else {
                    log.error("Failed to distribute event $event, failure outcome: $updateResult")
                    handlePossibleFatalTermination(event, updateResult as Try.Failure<String>)
                }
            }
        //}
    }

    private fun handlePossibleFatalTermination(event: NodeLifecycleEvent, updateFailed: Try.Failure<String>) {
        if (event.javaClass in criticalEventsClasses && updateFailed.exception.javaClass in criticalExceptionsClasses) {
            log.error("During processing of $event critical failures been reported: $updateFailed. JVM will be terminated.")
            exitProcess(1)
        }
    }

    /**
     * Custom implementation vs. using [kotlin.concurrent.withLock] to allow interruption during lock acquisition.
     */
    private fun <T> Lock.executeLocked(block: () -> T) : T {
        lockInterruptibly()
        try {
            return block()
        } finally {
            unlock()
        }
    }
}