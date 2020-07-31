package net.corda.nodeapi.internal.lifecycle

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.services.CordaServiceCriticalFailureException
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.nodeapi.internal.persistence.contextDatabaseOrNull
import java.io.Closeable
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
class NodeLifecycleEventsDistributor : Closeable {

    companion object {
        private val log = contextLogger()

        private val criticalEventsClasses: Set<Class<out NodeLifecycleEvent>> = setOf(
                NodeLifecycleEvent.BeforeNodeStart::class.java,
                NodeLifecycleEvent.AfterNodeStart::class.java,
                NodeLifecycleEvent.StateMachineStarted::class.java)
        private val criticalExceptionsClasses: Set<Class<out Throwable>> = setOf(CordaServiceCriticalFailureException::class.java)
    }

    /**
     * Order is maintained by priority and within equal priority by full class name.
     */
    private val prioritizedObservers: MutableList<NodeLifecycleObserver> = mutableListOf()

    private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()

    private val executor = Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("NodeLifecycleEventsDistributor-%d").setDaemon(true).build())

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
                    // Reversing sorting order such that higher priorities come first
                    return other.priority - priority
                }
                // Within the same priority order alphabetically by class name to deterministic order
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
     *
     * @return [CordaFuture] to signal when distribution is finished and delivered to all the observers
     */
    fun distributeEvent(event: NodeLifecycleEvent): CordaFuture<Unit> {
        val snapshot = readWriteLock.readLock().executeLocked { LinkedList(prioritizedObservers) }

        val result = openFuture<Any?>()

        if(executor.isShutdown || executor.isTerminated) {
            log.warn("Not distributing $event as executor been already shutdown. Double close() case?")
            result.set(null)
        } else {

            val passTheDbToTheThread = contextDatabaseOrNull

            executor.execute {

                if (passTheDbToTheThread != null)
                    contextDatabase = passTheDbToTheThread

                val orderedSnapshot = if (event.reversedPriority) snapshot.reversed() else snapshot
                orderedSnapshot.forEach {
                    log.debug("Distributing event $event to: $it")
                    val updateResult = it.update(event)
                    when(updateResult) {
                        is Try.Success -> log.debug("Event $event distribution outcome: $updateResult")
                        is Try.Failure -> {
                            log.error("Failed to distribute event $event, failure outcome: $updateResult", updateResult.exception)
                            handlePossibleFatalTermination(event, updateResult)
                        }
                    }
                }
                result.set(null)
            }
        }
        return result.map { }
    }

    private fun handlePossibleFatalTermination(event: NodeLifecycleEvent, updateFailed: Try.Failure<String>) {
        if (event.javaClass in criticalEventsClasses && updateFailed.exception.javaClass in criticalExceptionsClasses) {
            log.error("During processing of $event critical failure been reported: $updateFailed. JVM will be terminated.")
            exitProcess(1)
        } else {
            log.warn("During processing of $event non-critical failure been reported: $updateFailed.")
        }
    }

    override fun close() {
        executor.shutdown()
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