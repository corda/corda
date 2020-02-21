@file:JvmName("Observables")
package net.corda.core.observable

import net.corda.core.observable.internal.OnResilientSubscribe
import rx.Observable

/**
 * [Observable.continueOnError] is used to return an Observable, through which we can subscribe non unsubscribing [rx.Observer]s
 * to the source [Observable]. Namely, it makes the [rx.Observer]s resilient to exceptions coming out of [rx.Observer.onNext].
 *
 * [Observable.continueOnError] should be called before every subscribe to have the aforementioned effect.
 */
fun <T> Observable<T>.continueOnError(): Observable<T> = Observable.unsafeCreate(OnResilientSubscribe(this, true))