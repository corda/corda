package net.corda.core.concurrent

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Unless otherwise documented, methods have the same behaviour as the corresponding methods on [Future]. */
interface CordaFuture<out V> { // We don't extend JDK Future so that V can have out variance.
    fun cancel(mayInterruptIfRunning: Boolean): Boolean
    fun isCancelled(): Boolean
    fun isDone(): Boolean
    @Throws(InterruptedException::class, ExecutionException::class)
    fun get(): V

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun get(timeout: Long, unit: TimeUnit): V

    /** @return the underlying [Future], for JDK interoperability. */
    fun unwrap(): Future<out V>

    /**
     * Run the given callback when this future is done, on the completion thread.
     * If the completion thread is problematic for you e.g. deadlock, you can submit to an executor manually.
     * If callback fails, its throwable is logged.
     */
    fun <W> then(callback: (CordaFuture<V>) -> W): Unit
}

interface ApiFuture<V> : CordaFuture<V>, Future<V>
