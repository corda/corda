package net.corda.core.concurrent

import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future

/** @return a fresh [OpenFuture]. */
fun <V> openFuture(): OpenFuture<V> = CordaFutureImpl()

/** @return a done future with the given value as its outcome. */
fun <V> doneFuture(value: V): CordaFuture<V> = CordaFutureImpl<V>().apply { set(value) }

/** @return a future that will have the same outcome as the given block, when this executor has finished running it. */
fun <V> Executor.fork(block: () -> V): CordaFuture<V> = CordaFutureImpl<V>().also { execute { it.capture(block) } }

/** Unless otherwise documented, methods have the same behaviour as the corresponding methods on [Future]. */
interface CordaFuture<out V> { // We don't extend JDK Future so that V can have out variance.
    fun cancel(mayInterruptIfRunning: Boolean): Boolean
    val isCancelled: Boolean
    val isDone: Boolean
    fun get(): V
    fun get(timeout: Duration): V
    /** @see [net.corda.core.concurrent.getOrThrow] */
    fun getOrThrow(): V

    /** @see [net.corda.core.concurrent.getOrThrow] */
    fun getOrThrow(timeout: Duration): V

    /** @return the underlying [Future], for JDK interoperability. */
    fun unwrap(): Future<out V>

    /** Same as [net.corda.core.concurrent.match], which blocks if this isn't done. See [thenMatch] for non-blocking behaviour. */
    fun <W> match(success: (V) -> W, failure: (Throwable) -> W): W

    /**
     * Low-level method to run the given block when this future becomes done, on the completion thread.
     * If the completion thread is problematic for you e.g. deadlock, you can submit to an executor manually.
     * If block fails, its throwable is logged.
     */
    fun <W> then(block: (CordaFuture<V>) -> W)

    /** When this future is done, do [match]. */
    fun <W, X> thenMatch(success: (V) -> W, failure: (Throwable) -> X) = then { match(success, failure) }

    /** When this future is done and the outcome is failure, log the throwable. */
    fun andForget(log: Logger) = thenMatch({}, { log.error("Background task failed:", it) })

    /**
     * Returns a future that will have an outcome of applying the given transform to this future's value.
     * But if this future fails, the transform is not invoked and the returned future becomes done with the same throwable.
     */
    fun <W> map(transform: (V) -> W): CordaFuture<W> = CordaFutureImpl<W>().also { g ->
        thenMatch({
            g.capture { transform(it) }
        }, {
            g.setException(it)
        })
    }

    /**
     * Returns a future that will have the same outcome as the future returned by the given transform.
     * But if this future or the transform fails, the returned future's outcome is the same throwable.
     * In the case where this future fails, the transform is not invoked.
     */
    fun <W> flatMap(transform: (V) -> CordaFuture<W>): CordaFuture<W> = CordaFutureImpl<W>().also { g ->
        thenMatch(success@ {
            g.captureLater(try {
                transform(it)
            } catch (t: Throwable) {
                g.setException(t)
                return@success
            })
        }, {
            g.setException(it)
        })
    }
}

interface ValueOrException<in V> {
    /** @return the underlying [Future], for JDK interoperability. */
    fun unwrap(): CompletableFuture<in V>

    /** @return whether this future actually changed. */
    fun set(value: V): Boolean

    /** @return whether this future actually changed. */
    fun setException(t: Throwable): Boolean

    /** When the given future has an outcome, make this future have the same outcome. */
    fun captureLater(f: CordaFuture<V>) = f.then { capture { f.getOrThrow() } }

    /** Run the given block (in the foreground) and set this future to its outcome. */
    fun capture(block: () -> V): Boolean {
        return set(try {
            block()
        } catch (t: Throwable) {
            return setException(t)
        })
    }
}

interface OpenFuture<V> : ValueOrException<V>, CordaFuture<V> {
    override fun unwrap(): CompletableFuture<V>
}

/** Unless you really want this particular implementation, use [openFuture] to make one. */
internal class CordaFutureImpl<V> : OpenFuture<V> {
    companion object {
        private val defaultLog = loggerFor<CordaFutureImpl<*>>()
        internal val listenerFailedMessage = "Future listener failed:"
    }

    private val impl = CompletableFuture<V>()
    override fun unwrap() = impl
    override fun cancel(mayInterruptIfRunning: Boolean) = impl.cancel(mayInterruptIfRunning)
    override val isCancelled get() = impl.isCancelled
    override val isDone get() = impl.isDone
    override fun get(): V = impl.get()
    override fun get(timeout: Duration): V = impl.get(timeout)
    override fun getOrThrow(): V = impl.getOrThrow()
    override fun getOrThrow(timeout: Duration): V = impl.getOrThrow(timeout)
    override fun <W> match(success: (V) -> W, failure: (Throwable) -> W) = impl.match(success, failure)
    override fun set(value: V) = impl.complete(value)
    override fun setException(t: Throwable) = impl.completeExceptionally(t)
    override fun <W> then(block: (CordaFuture<V>) -> W) = thenImpl(defaultLog, block)
    /** For testing only. */
    internal fun <W> thenImpl(log: Logger, block: (CordaFuture<V>) -> W) {
        impl.whenComplete { _, _ ->
            try {
                block(this)
            } catch (t: Throwable) {
                log.error(listenerFailedMessage, t)
            }
        }
    }
}
