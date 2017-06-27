package net.corda.core

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

object MoreFutures {
    /**
     * As soon as a given future becomes done, the handler is invoked with that future as its argument.
     * The result of the handler is copied into the result future, and the handler isn't invoked again.
     * If a given future errors after the result future is done, the error is automatically logged.
     */
    fun <S, T> firstOf(vararg futures: ListenableFuture<out S>, handler: (ListenableFuture<out S>) -> T) = firstOf(futures, defaultLog, handler)

    private val defaultLog = loggerFor<MoreFutures>()
    @VisibleForTesting
    internal val shortCircuitedTaskFailedMessage = "Short-circuited task failed:"

    internal fun <S, T> firstOf(futures: Array<out ListenableFuture<out S>>, log: Logger, handler: (ListenableFuture<out S>) -> T): ListenableFuture<T> {
        val resultFuture = SettableFuture.create<T>()
        val winnerChosen = AtomicBoolean()
        futures.forEach {
            it.then {
                if (winnerChosen.compareAndSet(false, true)) {
                    resultFuture.catch { handler(it) }
                } else if (!it.isCancelled) {
                    it.failure { log.error(shortCircuitedTaskFailedMessage, it) }
                }
            }
        }
        return resultFuture
    }
}
