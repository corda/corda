package net.corda.core

import com.google.common.util.concurrent.AbstractFuture
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * As soon as a given future becomes done, the handler is invoked with that future as its argument.
 * Invocations of the handler will not run concurrently, so it's safe for it to maintain state.
 * The result of the handler is copied into the result future, and the handler isn't invoked again.
 * If a given future errors after the result future is done, the error is automatically logged.
 */
fun <T> Iterable<ListenableFuture<*>>.then(handler: (ListenableFuture<*>) -> T): ListenableFuture<T> {
    return ThenContextImpl(this, handler = handler)
}

internal class ThenContextImpl<T>(futures: Iterable<ListenableFuture<*>>, private val log: Logger = defaultLog, handler: (ListenableFuture<*>) -> T) : AbstractFuture<T>() {
    companion object {
        private val defaultLog = loggerFor<ThenContextImpl<*>>()
        internal val shortCircuitedTaskFailedMessage = "Short-circuited task failed:"
    }

    init {
        val lock = ReentrantLock()
        val queue = LinkedList<ListenableFuture<*>>()
        futures.forEach {
            it.then {
                val drain = !lock.isHeldByCurrentThread
                lock.withLock {
                    queue.add(it)
                    if (drain) {
                        while (!queue.isEmpty()) { // The handler may add to the queue.
                            val doneFuture = queue.removeFirst()
                            if (isDone) {
                                if (!doneFuture.isCancelled) doneFuture.failure { log.error(shortCircuitedTaskFailedMessage, it) }
                            } else {
                                ErrorOr.catch { handler(doneFuture) }.match({
                                    set(it)
                                }) {
                                    setException(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
