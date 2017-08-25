package net.corda.node.utilities

import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions
import java.time.Clock
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import com.google.common.util.concurrent.SettableFuture as GuavaSettableFuture

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