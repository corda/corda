@file:JvmName("Utils")

package net.corda.core

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.concurrent.thenMatch
import rx.Observable
import rx.Observer

// TODO Delete this file once the Future stuff is out of here

fun <A> CordaFuture<out A>.toObservable(): Observable<A> {
    return Observable.create { subscriber ->
        thenMatch({
            subscriber.onNext(it)
            subscriber.onCompleted()
        }, {
            subscriber.onError(it)
        })
    }
}

/**
 * Returns a [CordaFuture] bound to the *first* item emitted by this Observable. The future will complete with a
 * NoSuchElementException if no items are emitted or any other error thrown by the Observable. If it's cancelled then
 * it will unsubscribe from the observable.
 */
fun <T> Observable<T>.toFuture(): CordaFuture<T> = openFuture<T>().also {
    val subscription = first().subscribe(object : Observer<T> {
        override fun onNext(value: T) {
            it.set(value)
        }

        override fun onError(e: Throwable) {
            it.setException(e)
        }

        override fun onCompleted() {}
    })
    it.then {
        if (it.isCancelled) {
            subscription.unsubscribe()
        }
    }
}
