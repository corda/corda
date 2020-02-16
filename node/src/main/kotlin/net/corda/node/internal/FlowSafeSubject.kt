package net.corda.node.internal

import net.corda.core.internal.FlowSafeSubscriber
import net.corda.core.internal.VisibleForTesting
import rx.Observable.OnSubscribe
import rx.Observer
import rx.observers.SafeSubscriber
import rx.subjects.Subject

/**
 * [FlowSafeSubject] is used to unwrap an [Observer] from a [SafeSubscriber], re-wrap it with a [FlowSafeSubscriber]
 * and then subscribe it to its underlying [Subject]. It is only used to provide therefore, its underlying [Subject] with
 * non unsubscribing [rx.Observer]s.
 *
 * Upon [rx.Observable.subscribe] it will wrap everything that is a non [SafeSubscriber] with a [FlowSafeSubscriber] the same way
 * [rx.subjects.PublishSubject] wraps everything that is a non [SafeSubscriber] with a [SafeSubscriber].
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

/**
 * The [PreventSubscriptionsSubject] is used to prevent any subscriptions to its underlying [Subject].
 */
class PreventSubscriptionsSubject<T, R>(private val actual: Subject<T, R>, errorAction: () -> Unit) : Observer<T> by actual,
    Subject<T, R>(OnSubscribe<R> { _ ->
        errorAction()
    }) {

    override fun hasObservers(): Boolean {
        return actual.hasObservers()
    }
}