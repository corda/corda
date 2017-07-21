package net.corda.node.utilities

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SettableFuture
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.internal.until
import net.corda.core.then
import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions
import java.time.Clock
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiConsumer
import com.google.common.util.concurrent.SettableFuture as GuavaSettableFuture

/**
 * The classes and methods in this file allow the use of custom Clocks in demos, simulations and testing
 * that might not follow "real time" or "wall clock time".  i.e. they allow time to be fast forwarded.
 *
 * We should try to make the Clock used in our code injectable (for tests etc) and to use the extensions below
 * to wait in our code, rather than <code>Thread.sleep()</code> or <code>Object.wait()</code> etc.
 */

/**
 * An abstract class with helper methods for a type of Clock that might have it's concept of "now"
 * adjusted externally.
 *
 * e.g. for testing (so unit tests do not have to wait for timeouts in realtime) or for demos and simulations.
 */
abstract class MutableClock : Clock() {

    private val _version = AtomicLong(0L)

    /**
     * This is an observer on the mutation count of this [Clock], which reflects the occurence of mutations.
     */
    val mutations: Observable<Long> by lazy {
        Observable.create({ subscriber: Subscriber<in Long> ->
            if (!subscriber.isUnsubscribed) {
                mutationObservers.add(subscriber)
                // This is not very intuitive, but subscribing to a subscriber observes unsubscribes.
                subscriber.add(Subscriptions.create { mutationObservers.remove(subscriber) })
            }
        })
    }

    private val mutationObservers = CopyOnWriteArraySet<Subscriber<in Long>>()

    /**
     * Must be called by subclasses when they mutate (but not just with the passage of time as per the "wall clock").
     */
    protected fun notifyMutationObservers() {
        val version = _version.incrementAndGet()
        for (observer in mutationObservers) {
            if (!observer.isUnsubscribed) {
                observer.onNext(version)
            }
        }
    }
}

/**
 * Wait until the given [Future] is complete or the deadline is reached, with support for [MutableClock] implementations
 * used in demos or testing.  This will substitute a Fiber compatible Future so the current
 * [co.paralleluniverse.strands.Strand] is not blocked.
 *
 * @return true if the [Future] is complete, false if the deadline was reached.
 */
@Suspendable
fun Clock.awaitWithDeadline(deadline: Instant, future: Future<*> = GuavaSettableFuture.create<Any>()): Boolean {
    var nanos: Long
    do {
        val originalFutureCompleted = makeStrandFriendlySettableFuture(future)
        val subscription = if (this is MutableClock) {
            mutations.first().subscribe {
                originalFutureCompleted.set(false)
            }
        } else {
            null
        }
        nanos = (instant() until deadline).toNanos()
        if (nanos > 0) {
            try {
                // This will return when it times out, or when the clock mutates or when when the original future completes.
                originalFutureCompleted.get(nanos, TimeUnit.NANOSECONDS)
            } catch(e: ExecutionException) {
                // No need to take action as will fall out of the loop due to future.isDone
            } catch(e: CancellationException) {
                // No need to take action as will fall out of the loop due to future.isDone
            } catch(e: TimeoutException) {
                // No need to take action as will fall out of the loop due to future.isDone
            }
        }
        subscription?.unsubscribe()
        originalFutureCompleted.cancel(false)
    } while (nanos > 0 && !future.isDone)
    return future.isDone
}

/**
 * Convert a Guava [ListenableFuture] or JDK8 [CompletableFuture] to Quasar implementation and set to true when a result
 * or [Throwable] is available in the original.
 *
 * We need this so that we do not block the actual thread when calling get(), but instead allow a Quasar context
 * switch.  There's no need to checkpoint our Fibers as there's no external effect of waiting.
 */
private fun <T : Any> makeStrandFriendlySettableFuture(future: Future<T>): SettableFuture<Boolean> {
    return if (future is ListenableFuture) {
        val settable = SettableFuture<Boolean>()
        future.then { settable.set(true) }
        settable
    } else if (future is CompletableFuture) {
        val settable = SettableFuture<Boolean>()
        future.whenComplete(BiConsumer { _, _ -> settable.set(true) })
        settable
    } else {
        throw IllegalArgumentException("Cannot make future $future Fiber friendly.")
    }
}


