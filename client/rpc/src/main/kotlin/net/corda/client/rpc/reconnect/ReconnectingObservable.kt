package net.corda.client.rpc.reconnect

import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.DoNotImplement
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.DataFeed
import rx.Observable

/**
 * Returned as the `updates` field when calling methods that return a [DataFeed] on the [ReconnectingCordaRPCOps].
 *
 * TODO - provide a logical function to know how to retrieve missing events that happened during disconnects.
 */
@DoNotImplement
interface ReconnectingObservable<T> {
    fun subscribe(onNext: (T) -> Unit): ObserverHandle = subscribe(onNext, {}, {}, {})
    fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle
    fun startWithValues(values: Iterable<T>): ReconnectingObservable<T>
}

/**
 * Mainly for Kotlin users.
 */
fun <T> Observable<T>.asReconnecting(): ReconnectingObservable<T> = uncheckedCast(this)