package com.r3corda.node.utilities

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.AbstractFuture
import co.paralleluniverse.strands.SettableFuture
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.SuspendableRunnable
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.then
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.subscriptions.Subscriptions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiConsumer

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
     * This tracks how many direct mutations of "now" have occured for this [Clock], but not the passage of time.
     *
     * It starts at zero, and increments by one per mutation.
     */
    val mutationCount: Long
        get() = _version.get()

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
 * Internal method to do something that can be interrupted by a [MutableClock] in the case
 * of it being mutated.  Just returns if [MutableClock] mutates, so needs to be
 * called in a loop.
 *
 * @throws InterruptedException if interrupted by something other than a [MutableClock].
 */
@Suppress("UNUSED_VALUE") // This is here due to the compiler thinking version is not used
@Suspendable
private fun Clock.doInterruptibly(runnable: SuspendableRunnable) {
    var version = 0L
    var subscription: Subscription? = null
    try {
        if (this is MutableClock) {
            version = this.mutationCount
            val strand = Strand.currentStrand()
            subscription = this.mutations.subscribe { strand.interrupt() }
        }
        runnable.run()
    } catch(e: InterruptedException) {
        // If clock has not mutated, then re-throw
        val newVersion = if (this is MutableClock) this.mutationCount else version
        if (newVersion == version) {
            throw e
        }
    } finally {
        if (this is MutableClock) {
            subscription!!.unsubscribe()
        }
        Strand.interrupted()
    }
}

/**
 * Wait until the given [Future] is complete or the deadline is reached, with support for [MutableClock] implementations
 * used in demos or testing.  This will also substitute a Fiber compatible Future if required.
 *
 * @return true if the [Future] is complete, false if the deadline was reached.
 */
@Suspendable
fun Clock.awaitWithDeadline(deadline: Instant, future: Future<*> = SettableFuture<Any>()): Boolean {
    // convert the future to a strand friendly variety if possible so as not to accidentally block the underlying thread
    val fiberFriendlyFuture = makeFutureCurrentStrandFriendly(future)

    var nanos = 0L
    do {
        doInterruptibly(SuspendableRunnable @Suspendable {
            nanos = Duration.between(this.instant(), deadline).toNanos()
            if (nanos > 0) {
                try {
                    fiberFriendlyFuture.get(nanos, TimeUnit.NANOSECONDS)
                } catch(e: ExecutionException) {
                    // No need to take action as will fall out of the loop due to future.isDone
                } catch(e: CancellationException) {
                    // No need to take action as will fall out of the loop due to future.isDone
                }
            }
        })
    } while (!future.isDone && nanos > 0)
    return future.isDone
}

/**
 * Convert a Guava [ListenableFuture] or JDK8 [CompletableFuture] to Quasar implementation if currently
 * on a Fiber and not already using Quasar futures.
 *
 * We need this so that we do not block the actual thread when calling get(), but instead allow a Quasar context
 * switch.  There's no need to checkpoint our Fibers as there's no external effect of waiting.
 */
private fun <T : Any> makeFutureCurrentStrandFriendly(future: Future<T>): Future<out T> {
    return if (Strand.isCurrentFiber() && future !is AbstractFuture) {
        if (future is ListenableFuture) {
            val settable = SettableFuture<T>()
            future.then { settable.set(null) }
            settable
        } else if (future is CompletableFuture) {
            val settable = SettableFuture<T>()
            future.whenComplete(BiConsumer { value, throwable -> settable.set(null) })
            settable
        } else {
            throw IllegalArgumentException("Cannot make future $future Fiber friendly whilst on a Fiber")
        }
    } else future
}


