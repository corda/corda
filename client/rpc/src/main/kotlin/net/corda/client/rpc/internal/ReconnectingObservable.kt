package net.corda.client.rpc.internal

import net.corda.core.messaging.DataFeed

/**
 * Returned as the `updates` field when calling methods that return a [DataFeed] on the [ReconnectingCordaRPCOps].
 *
 * TODO - provide a logical function to know how to retrieve missing events that happened during disconnects.
 */
interface ReconnectingObservable<T> {
    fun subscribe(onNext: (T) -> Unit): ObserverHandle = subscribe(onNext, {}, {}, {})
    fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle
    fun startWithValues(values: Iterable<T>): ReconnectingObservable<T>
}
