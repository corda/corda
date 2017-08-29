package net.corda.core.internal.concurrent

import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.match
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.CordaFutures.Companion.openFuture
import net.corda.core.internal.concurrent.CordaFutures.Companion.thenMatch
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** The contravariant members of [OpenFuture]. */
interface ValueOrException<in V> {
    /** @return whether this future actually changed. */
    fun set(value: V): Boolean

    /** @return whether this future actually changed. */
    fun setException(t: Throwable): Boolean

    /** When the given future has an outcome, make this future have the same outcome. */
    fun captureLater(f: CordaFuture<out V>) = f.then { capture { f.getOrThrow() } }

    /** Run the given block (in the foreground) and set this future to its outcome. */
    fun capture(block: () -> V): Boolean {
        return set(try {
            block()
        } catch (t: Throwable) {
            return setException(t)
        })
    }
}

/**
 * A convenience class providing utility functions around the CordaFuture class.
 */
class CordaFutures {
    companion object {
        /**
         * Creates an instance of [OpenFuture].
         *
         * @param V a future value type.
         * @return a fresh [OpenFuture].
         */
        @JvmStatic
        fun <V> openFuture(): OpenFuture<V> = CordaFutureImpl()

        /**
         * Creates an instance of [CordaFuture] being already completed with a given value.
         *
         * @param V a future value type.
         * @param value an outcome of the created future.
         * @return a done future with the given value as its outcome.
         */
        @JvmStatic
        fun <V> doneFuture(value: V): CordaFuture<V> = CordaFutureImpl<V>().apply { set(value) }

        /**
         * Creates an instance of [CordaFuture], which value will be the outcome of the lambda, executed by the [executor].
         *
         * @param V a future value type.
         * @param executor to execute the given lambda.
         * @param block a lambda, which result will be the future outcome.
         * @return a future that will have the same outcome as the given block, when this executor has finished running it.
         */
        @JvmStatic
        fun <V> fork(executor: Executor, block: () -> V): CordaFuture<V> = CordaFutureImpl<V>().also { executor.execute { it.capture(block) } }

        /** Once the given future is done, it matches (@see [match]) its result against the [success] and [failure].
         *
         * @param future a future, which outcome is to be matched.
         * @param success invoked when the future finished successfully. The outcome of the future is passed as input.
         * @param failure invoked when the future finished with an exception. The exception is passed as input.
         */
        @JvmStatic
        fun <V, W, X> thenMatch(future: CordaFuture<out V>, success: (V) -> W, failure: (Throwable) -> X) = future.then { future.match(success, failure) }

        /**
         * When the future is done and the outcome is failure, log the throwable.
         *
         * @param future which outcome is matched.
         * @param log logger to be used for logging the error in case of future failure.
         */
        @JvmStatic
        fun andForget(future: CordaFuture<*>, log: Logger) = thenMatch(future, {}, { log.error("Background task failed:", it) })

        /**
         * Applies the [transform] function to the outcome of the [future] and returns new  future with the result
         * of that transformation as the outcome. If the original future fails, the transform is not invoked and
         * the returned future becomes done with the same throwable.
         *
         * @param future future which value is to be transformed.
         * @param transform transformation to be applied.
         * @return a future holding either the result of the transformation or the original future failure cause.
         *
         */
        @JvmStatic
        fun <V, W> map(future: CordaFuture<out V>, transform: (V) -> W): CordaFuture<W> = CordaFutureImpl<W>().also { result ->
            thenMatch(future, {
                result.capture { transform(it) }
            }, {
                result.setException(it)
            })
        }

        /**
         * Calls transform on the outcome of the given [future] and returns a future that will have the same outcome
         * as the future returned by the transform function. If the future (passed as a parameter)
         * or the transform fails the returned future's outcome is the same throwable and the transform is not invoked.
         *
         * @param future future which value is to be transformed.
         * @param transform transformation to be applied.
         * @return a future holding either the same result as the future given by the transformation or the original future failure cause.
         */
        @JvmStatic
        fun <V, W> flatMap(future: CordaFuture<out V>, transform: (V) -> CordaFuture<out W>): CordaFuture<W> = CordaFutureImpl<W>().also { result ->
            thenMatch(future, success@ {
                result.captureLater(try {
                    transform(it)
                } catch (t: Throwable) {
                    result.setException(t)
                    return@success
                })
            }, {
                result.setException(it)
            })
        }

        /**
         * If all of the given futures succeed, the returned future's outcome is a list of all their values.
         * The values are in the same order as the futures in the collection and not in the order of their completion.
         * If at least one given future fails, the returned future's outcome is the first throwable that was thrown.
         * Any subsequent throwables are added to the first one as suppressed throwables, in the order they are thrown.
         * If no futures were given, the returned future has an immediate outcome of empty list.
         * Otherwise the returned future does not have an outcome until all given futures have an outcome.
         * Unlike Guava's Futures.allAsList, this method never hides failures/hangs subsequent to the first failure.
         *
         * @param futures a collection of futures to be transposed.
         */
        @JvmStatic
        fun <V> transpose(futures: Collection<CordaFuture<out V>>): CordaFuture<List<V>> {
            if (futures.isEmpty()) return doneFuture(emptyList())
            val transpose = CordaFutureImpl<List<V>>()
            val stateLock = Any()
            var failure: Throwable? = null
            var remaining = futures.size
            futures.forEach {
                it.then { doneFuture ->
                    synchronized(stateLock) {
                        doneFuture.match({}, { throwable ->
                            if (failure == null) failure = throwable else failure!!.addSuppressed(throwable)
                        })
                        if (--remaining == 0) {
                            if (failure == null) transpose.set(futures.map { it.getOrThrow() }) else transpose.setException(failure!!)
                        }
                    }
                }
            }
            return transpose
        }
    }
}

/** A [CordaFuture] with additional methods to complete it with a value, exception or the outcome of another future. */
interface OpenFuture<V> : ValueOrException<V>, CordaFuture<V>

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
        thenMatch(this, {
            completable.complete(it)
        }, {
            completable.completeExceptionally(it)
        })
    }
}

internal fun <V> Future<V>.get(timeout: Duration? = null): V = if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
