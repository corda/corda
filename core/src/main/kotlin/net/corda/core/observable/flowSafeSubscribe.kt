package net.corda.core.observable

import net.corda.core.observable.internal.FlowSafeSubscriber
import rx.Observable
import rx.Subscriber
import rx.observers.SafeSubscriber

/**
 * [Observable.flowSafeSubscribe] is used to return an Observable, through which we can subscribe non unsubscribing [rx.Observer]s
 * to the source [Observable].
 *
 * It unwraps an [Observer] from a [SafeSubscriber], re-wraps it with a [FlowSafeSubscriber] and then subscribes it
 * to the source [Observable].
 *
 * In case we need to subscribe with a [SafeSubscriber] via [flowSafeSubscribe], we have to:
 * 1. Declare a custom SafeSubscriber extending [SafeSubscriber].
 * 2. Wrap with the custom SafeSubscriber the [rx.Observer] to be subscribed to the source [Observable].
 * 3. Call [Observable.flowSafeSubscribe] with [strictMode] = false
 * 4. Subscribe to the returned [Observable] passing in as argument the custom SafeSubscriber.
 */
fun <T> Observable<T>.flowSafeSubscribe(strictMode: Boolean = false): Observable<T> {

    class OnFlowSafeSubscribe<T>(val source: Observable<T>): Observable.OnSubscribe<T> {

        override fun call(subscriber: Subscriber<in T>) {
            if (isSafeSubscriber(subscriber)) {
                source.unsafeSubscribe(FlowSafeSubscriber((subscriber as SafeSubscriber).actual))
            } else {
                source.unsafeSubscribe(subscriber)
            }
        }

        private fun isSafeSubscriber(subscriber: Subscriber<*>): Boolean {
            return if (strictMode) {
                // In strictMode mode we capture SafeSubscriber subclasses as well
                SafeSubscriber::class.java.isAssignableFrom(subscriber::class.java)
            } else {
                subscriber::class == SafeSubscriber::class
            }
        }
    }

    return Observable.unsafeCreate(OnFlowSafeSubscribe(this))
}