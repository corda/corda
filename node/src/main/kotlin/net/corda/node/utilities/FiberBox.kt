package net.corda.node.utilities

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.concurrent.ReentrantLock
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.RetryableException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

// TODO: We should consider using a Semaphore or CountDownLatch here to make it a little easier to understand, but it seems as though the current version of Quasar does not support suspending on either of their implementations.

/**
 * Modelled on [net.corda.core.ThreadBox], but with support for waiting that is compatible with Quasar [Fiber]s and [MutableClock]s.
 *
 * It supports 3 main operations, all of which operate in a similar context to the [locked] method
 * of [net.corda.core.ThreadBox].  i.e. in the context of the content.
 * * [read] operations which acquire the associated lock but do not notify any waiters (see [readWithDeadline])
 * and is a direct equivalent of [net.corda.core.ThreadBox.locked].
 * * [write] operations which are the same as [read] operations but additionally notify any waiters that the content may have changed.
 * * [readWithDeadline] operations acquire the lock and are evaluated repeatedly until they no longer throw any subclass
 * of [RetryableException].  Between iterations it will wait until woken by a [write] or the deadline is reached.  It will eventually
 * re-throw a [RetryableException] if the deadline passes without any successful iterations.
 *
 * The construct also supports [MutableClock]s so it can cope with artificial progress towards the deadline, for simulations
 * or testing.
 *
 * Currently this is intended for use within a node as a simplified way for Oracles to implement subscriptions for changing
 * data by running a flow internally to implement the request handler which can then
 * effectively relinquish control until the data becomes available.  This isn't the most scalable design and is intended
 * to be temporary.  In addition, it's enitrely possible to envisage a time when we want public [net.corda.core.flows.FlowLogic]
 * implementations to be able to wait for some condition to become true outside of message send/receive.  At that point
 * we may revisit this implementation and indeed the whole model for this, when we understand that requirement more fully.
 */
// TODO This is no longer used and can be removed
class FiberBox<out T>(private val content: T, private val lock: Lock = ReentrantLock()) {
    private var mutated: SettableFuture<Boolean>? = null

    @Suppress("UNUSED_VALUE")  // This is here due to the compiler thinking ourMutated is not used
    @Suspendable
    fun <R> readWithDeadline(clock: Clock, deadline: Instant, body: T.() -> R): R {
        var ex: Exception
        var ourMutated: Future<Boolean>? = null
        do {
            lock.lock()
            try {
                if (mutated == null || mutated!!.isDone) {
                    mutated = SettableFuture.create()
                }
                ourMutated = mutated
                return body(content)
            } catch(e: RetryableException) {
                ex = e
            } finally {
                lock.unlock()
            }
        } while (clock.awaitWithDeadline(deadline, ourMutated!!) && clock.instant().isBefore(deadline))
        throw ex
    }

    @Suspendable
    fun <R> read(body: T.() -> R): R = lock.withLock { body(content) }

    @Suspendable
    fun <R> write(body: T.() -> R): R {
        lock.lock()
        try {
            return body(content)
        } finally {
            mutated?.set(true)
            lock.unlock()
        }
    }
}
