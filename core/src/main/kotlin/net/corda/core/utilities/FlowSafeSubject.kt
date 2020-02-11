package net.corda.core.utilities

import net.corda.core.internal.FlowSafeSubscriber
import net.corda.core.internal.VisibleForTesting
import rx.Observable.OnSubscribe
import rx.Observer
import rx.Subscriber
import rx.observers.SafeSubscriber
import rx.subjects.Subject

/**
 * The [FlowSafeSubject] is used to unwrap a [SafeSubscriber] to prevent the observer from unsubscribing from the base observable when any
 * error occurs. Calls to [SafeSubscriber._onError] call [Subscriber.unsubscribe] multiple times, which stops the observer receiving updates
 * from the observable.
 *
 * Preventing this is useful to observers that are subscribed to important observables to prevent them from ever unsubscribing due to an
 * error. Unsubscribing could lead to a malfunctioning CorDapp, for the rest of the current run time, due to a single isolated error.
 */
@VisibleForTesting
class FlowSafeSubject<T, R>(private val actual: Subject<T, R>) : Observer<T> by actual,
    Subject<T, R>(OnSubscribe<R> { subscriber ->
        if (subscriber::class == SafeSubscriber::class) {
            actual.unsafeSubscribe(FlowSafeSubscriber((subscriber as SafeSubscriber).actual))
        } else {
            actual.unsafeSubscribe(subscriber)
        }
    }) {

    override fun hasObservers(): Boolean {
        return actual.hasObservers()
    }
}