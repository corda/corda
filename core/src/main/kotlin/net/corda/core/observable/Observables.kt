@file:JvmName("Observables")
package net.corda.core.observable

import net.corda.core.observable.internal.OnFlowSafeSubscribe
import rx.Observable

/**
 * [Observable.flowSafeObservable] is used to return an Observable, through which we can subscribe non unsubscribing [rx.Observer]s
 * to the source [Observable].
 */
fun <T> Observable<T>.flowSafeObservable(): Observable<T> = Observable.unsafeCreate(OnFlowSafeSubscribe(this, true))