package net.corda.core.concurrent

import com.google.common.annotations.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun <V> Future<V>.get(timeout: Duration? = null): V = if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)

/** Same as [Future.get] except that the [ExecutionException] is unwrapped. */
fun <V> Future<V>.getOrThrow(timeout: Duration? = null): V = try {
    get(timeout)
} catch (e: ExecutionException) {
    throw e.cause!!
}

/** Invoke [getOrThrow] and pass the value/throwable to success/failure respectively. */
fun <V, W> Future<V>.match(success: (V) -> W, failure: (Throwable) -> W): W {
    return success(try {
        getOrThrow()
    } catch (t: Throwable) {
        return failure(t)
    })
}

/**
 * If all of the given futures succeed, the returned future's outcome is a list of all their values.
 * The values are in the same order as the futures in the collection, not the order of completion.
 * If at least one given future fails, the returned future's outcome is the first throwable that was thrown.
 * Any subsequent throwables are added to the first one as suppressed throwables, in the order they are thrown.
 * If no futures were given, the returned future has an immediate outcome of empty list.
 * Otherwise the returned future does not have an outcome until all given futures have an outcome, so hangs propagate.
 * Unlike Guava's Futures.allAsList, this method never hides failures/hangs subsequent to the first failure.
 */
fun <V> Collection<CordaFuture<V>>.transpose(): CordaFuture<List<V>> = CordaFutureImpl<List<V>>().also { g ->
    if (isEmpty()) {
        g.set(emptyList())
    } else {
        val stateLock = Any()
        var failure: Throwable? = null
        var remaining = size
        forEach {
            it.then {
                synchronized(stateLock) {
                    it.match({}, {
                        failure?.addSuppressed(it) ?: run { failure = it }
                    })
                    if (--remaining == 0) {
                        failure?.let { g.setException(it) } ?: run { g.set(map { it.getOrThrow() }) }
                    }
                }
            }
        }
    }
}

/**
 * As soon as a given future becomes done, the handler is invoked with that future as its argument.
 * The result of the handler is copied into the result future, and the handler isn't invoked again.
 * If a given future errors after the result future is done, the error is automatically logged.
 */
fun <V, W> firstOf(vararg futures: CordaFuture<V>, handler: (CordaFuture<V>) -> W) = firstOf(futures, defaultLog, handler)

private val defaultLog = LoggerFactory.getLogger("net.corda.core.concurrent")
@VisibleForTesting
internal val shortCircuitedTaskFailedMessage = "Short-circuited task failed:"

internal fun <V, W> firstOf(futures: Array<out CordaFuture<V>>, log: Logger, handler: (CordaFuture<V>) -> W): CordaFuture<W> {
    val resultFuture = openFuture<W>()
    val winnerChosen = AtomicBoolean()
    futures.forEach {
        it.then {
            if (winnerChosen.compareAndSet(false, true)) {
                resultFuture.capture { handler(it) }
            } else if (it.isCancelled) {
                // Do nothing.
            } else {
                it.match({}, { log.error(shortCircuitedTaskFailedMessage, it) })
            }
        }
    }
    return resultFuture
}
