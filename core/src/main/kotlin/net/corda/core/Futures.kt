package net.corda.core

import com.google.common.util.concurrent.AbstractFuture
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

/**
 * Each future is passed into the given handler as soon as it becomes done.
 * By default invocations of the handler do not run concurrently, pass in null for [lock] to allow them to do so.
 * The handler should throw [ThenContext.thenAgain] if it does not want to affect the returned future.
 * Otherwise the result of the handler is copied into the returned future.
 * If the handler errors and the returned future is already done, the error is logged (unless it's thenAgain).
 * If the handler returns normally and the returned future is already done, the handler value is silently discarded.
 */
fun <T> Iterable<ListenableFuture<*>>.then(lock: Any? = Any(), handler: ThenContext.(ListenableFuture<*>) -> T): ListenableFuture<T> {
    return ThenContextImpl(this, handler = if (lock != null) { f -> synchronized(lock) { handler(f) } } else handler)
}

interface ThenContext {
    val thenAgain: Throwable
    /**
     * Whether the returned future is done, note another handler invocation may race this if you passed in null for lock.
     */
    fun isDone(): Boolean
}

internal class ThenContextImpl<T>(futures: Iterable<ListenableFuture<*>>, private val log: Logger = defaultLog, handler: ThenContext.(ListenableFuture<*>) -> T) : AbstractFuture<T>(), ThenContext {
    companion object {
        private val defaultLog = loggerFor<ThenContextImpl<*>>()
        private val thenAgainSingleton = Throwable()
    }

    override val thenAgain: Throwable = thenAgainSingleton

    init {
        futures.forEach {
            it then {
                ErrorOr.catch { handler(it) }.match({
                    set(it)
                }) {
                    if (it != thenAgain && !setException(it)) log.error("Short-circuited task failed:", it)
                }
            }
        }
    }
}
