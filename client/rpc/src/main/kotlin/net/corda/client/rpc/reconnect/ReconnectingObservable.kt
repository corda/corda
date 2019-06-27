package net.corda.client.rpc.reconnect

import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.DoNotImplement
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import rx.Observable
import java.util.concurrent.ExecutorService

class ReconnectingObservableImpl<T> internal constructor(
        val reconnectingSubscriber: ReconnectingSubscriber<T>
) : Observable<T>(reconnectingSubscriber), ReconnectingObservable<T> by reconnectingSubscriber {

    private companion object {
        private val log = contextLogger()
    }

    constructor(reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection, observersPool: ExecutorService, initial: DataFeed<*, T>, createDataFeed: () -> DataFeed<*, T>):
            this(ReconnectingSubscriber(reconnectingRPCConnection, observersPool, initial, createDataFeed))

    class ReconnectingSubscriber<T>(private val reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
                                    private val observersPool: ExecutorService,
                                    val initial: DataFeed<*, T>,
                                    val createDataFeed: () -> DataFeed<*, T>): OnSubscribe<T>, ReconnectingObservable<T> {
        override fun call(child: rx.Subscriber<in T>) {
            subscribe( {t -> child.onNext(t) }, {}, {}, {})
        }

        private var initialStartWith: Iterable<T>? = null
        fun _subscribeWithReconnect(observerHandle: ObserverHandle, onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit, startWithValues: Iterable<T>? = null) {
            var subscriptionError: Throwable?
            try {
                val subscription = initial.updates.let { if (startWithValues != null) it.startWith(startWithValues) else it }
                        .subscribe(onNext, observerHandle::fail, observerHandle::stop)
                subscriptionError = observerHandle.await()
                subscription.unsubscribe()
            } catch (e: Exception) {
                log.error("Failed to register subscriber .", e)
                subscriptionError = e
            }

            // In case there was no exception the observer has finished gracefully.
            if (subscriptionError == null) {
                onStop()
                return
            }

            onDisconnect()
            // Only continue if the subscription failed.
            reconnectingRPCConnection.error(subscriptionError)
            log.debug { "Recreating data feed." }

            val newObservable = createDataFeed().updates as ReconnectingObservableImpl<T>
            onReconnect()
            return newObservable.reconnectingSubscriber._subscribeWithReconnect(observerHandle, onNext, onStop, onDisconnect, onReconnect)
        }

        override fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle {
            val observerNotifier = ObserverHandle()
            // TODO - change the establish connection method to be non-blocking
            observersPool.execute {
                _subscribeWithReconnect(observerNotifier, onNext, onStop, onDisconnect, onReconnect, initialStartWith)
            }
            return observerNotifier
        }

        override fun startWithValues(values: Iterable<T>): ReconnectingObservable<T> {
            initialStartWith = values
            return this
        }

    }

}

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