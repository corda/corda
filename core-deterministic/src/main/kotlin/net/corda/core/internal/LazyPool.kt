package net.corda.core.internal

import net.corda.core.KeepForDJVM

/**
 * A lazy pool of resources [A], modified for DJVM.
 *
 * @param clear If specified this function will be run on each borrowed instance before handing it over.
 * @param shouldReturnToPool If specified this function will be run on each release to determine whether the instance
 *     should be returned to the pool for reuse. This may be useful for pooled resources that dynamically grow during
 *     usage, and we may not want to retain them forever.
 * @param bound If specified the pool will be bounded. Once all instances are borrowed subsequent borrows will block until an
 *     instance is released.
 * @param newInstance The function to call to lazily newInstance a pooled resource.
 */
@Suppress("unused")
@KeepForDJVM
class LazyPool<A>(
        private val clear: ((A) -> Unit)? = null,
        private val shouldReturnToPool: ((A) -> Boolean)? = null,
        private val bound: Int? = null,
        private val newInstance: () -> A
) {
    fun borrow(): A {
        return newInstance()
    }

    @Suppress("unused_parameter")
    fun release(instance: A) {
    }

    /**
     * Closes the pool. Note that all borrowed instances must have been released before calling this function, otherwise
     * the returned iterable will be inaccurate.
     */
    fun close(): Iterable<A> {
        return emptyList()
    }

    fun <R> reentrantRun(withInstance: (A) -> R): R = withInstance(borrow())
}
