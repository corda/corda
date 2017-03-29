package net.corda.core.utilities

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A lazy pool of resources [A].
 *
 * @param clear If specified this function will be run on each borrowed instance before handing it over.
 * @param bound If specified the pool will be bounded. Once all instances are borrowed subsequent borrows will block until an
 *     instance is released.
 * @param create The function to call to lazily create a pooled resource.
 */
class LazyPool<A>(
        private val clear: ((A) -> Unit)? = null,
        private val bound: Int? = null,
        private val create: () -> A
) {
    private val poolQueue = LinkedBlockingQueue<A>()
    private var poolSize = 0

    private enum class State {
        STARTED,
        FINISHED
    }
    private val lifeCycle = LifeCycle(State.STARTED)

    private fun clearIfNeeded(instance: A): A {
        clear?.invoke(instance)
        return instance
    }

    fun borrow(): A {
        lifeCycle.requireState(State.STARTED)
        val pooled = poolQueue.poll()
        if (pooled == null) {
            if (bound != null) {
                val waitForRelease = synchronized(this) {
                    if (poolSize < bound) {
                        poolSize++
                        false
                    } else {
                        true
                    }
                }
                if (waitForRelease) {
                    // Wait until one is released
                    return clearIfNeeded(poolQueue.take())
                }
            }
            return create()
        } else {
            return clearIfNeeded(pooled)
        }
    }

    fun release(instance: A) {
        lifeCycle.requireState(State.STARTED)
        poolQueue.add(instance)
    }

    /**
     * Closes the pool. Note that all borrowed instances must have been released before calling this function, otherwise
     * the returned iterable will be inaccurate.
     */
    fun close(): Iterable<A> {
        lifeCycle.transition(State.STARTED, State.FINISHED)
        return poolQueue
    }

    inline fun <R> run(withInstance: (A) -> R): R {
        val instance = borrow()
        try {
            return withInstance(instance)
        } finally {
            release(instance)
        }
    }
}