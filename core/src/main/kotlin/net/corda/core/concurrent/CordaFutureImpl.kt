package net.corda.core.concurrent

import net.corda.core.internal.VisibleForTesting
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.match
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Unless you really want this particular implementation, use [openFuture] to make one. */
@VisibleForTesting
internal class CordaFutureImpl<V>(private val impl: CompletableFuture<V> = CompletableFuture()) : Future<V> by impl, OpenFuture<V> {
    companion object {
        private val defaultLog = loggerFor<CordaFutureImpl<*>>()
        internal val listenerFailedMessage = "Future listener failed:"
    }

    override fun set(value: V) = impl.complete(value)
    override fun setException(t: Throwable) = impl.completeExceptionally(t)
    override fun <W> then(callback: (CordaFuture<V>) -> W) = thenImpl(defaultLog, callback)
    /** For testing only. */
    internal fun <W> thenImpl(log: Logger, callback: (CordaFuture<V>) -> W) {
        impl.whenComplete { _, _ ->
            try {
                callback(this)
            } catch (t: Throwable) {
                log.error(listenerFailedMessage, t)
            }
        }
    }

    // We don't simply return impl so that the caller can't interfere with it.
    override fun toCompletableFuture() = CompletableFuture<V>().also { completable ->
        thenMatch({
            completable.complete(it)
        }, {
            completable.completeExceptionally(it)
        })
    }
}
