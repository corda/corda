package net.corda.core.internal

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

/**
 * A lazy pool of resources [A].
 *
 * @param clear If specified this function will be run on each borrowed instance before handing it over.
 * @param shouldReturnToPool If specified this function will be run on each release to determine whether the instance
 *     should be returned to the pool for reuse. This may be useful for pooled resources that dynamically grow during
 *     usage, and we may not want to retain them forever.
 * @param bound If specified the pool will be bounded. Once all instances are borrowed subsequent borrows will block until an
 *     instance is released.
 * @param newInstance The function to call to lazily newInstance a pooled resource.
 */
class LazyPool<A>(
        private val clear: ((A) -> Unit)? = null,
        private val shouldReturnToPool: ((A) -> Boolean)? = null,
        private val bound: Int? = null,
        private val newInstance: () -> A
) {
    private val poolQueue = ConcurrentLinkedQueue<A>()
    private val poolSemaphore = Semaphore(bound ?: Int.MAX_VALUE)

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
        poolSemaphore.acquire()
        val pooled = poolQueue.poll()
        return if (pooled == null) {
            newInstance()
        } else {
            clearIfNeeded(pooled)
        }
    }

    fun release(instance: A) {
        lifeCycle.requireState(State.STARTED)
        if (shouldReturnToPool == null || shouldReturnToPool.invoke(instance)) {
            poolQueue.add(instance)
        }
        poolSemaphore.release()
    }

    /**
     * Closes the pool. Note that all borrowed instances must have been released before calling this function, otherwise
     * the returned iterable will be inaccurate.
     */
    fun close(): Iterable<A> {
        lifeCycle.justTransition(State.FINISHED)
        // Does not use kotlin toList() as it currently is not safe to use on concurrent data structures.
        val elements = ArrayList(poolQueue)
        poolQueue.clear()
        return elements
    }

    inline fun <R> run(withInstance: (A) -> R): R {
        val instance = borrow()
        try {
            return withInstance(instance)
        } finally {
            release(instance)
        }
    }

    private val currentBorrowed = ThreadLocal<A>()
    fun <R> reentrantRun(withInstance: (A) -> R): R {
        return currentBorrowed.get()?.let {
            withInstance(it)
        } ?: run {
            currentBorrowed.set(it)
            try {
                withInstance(it)
            } finally {
                currentBorrowed.set(null)
            }
        }
    }
}