/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import net.corda.core.DeleteForDJVM
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
@DeleteForDJVM
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
        val elements = poolQueue.toList()
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
}