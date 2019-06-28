package net.corda.client.rpc.internal

import net.corda.client.rpc.reconnect.ObserverHandle
import net.corda.client.rpc.reconnect.ReconnectingObservable
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
            subscribe(child::onNext, {}, {}, {})
        }

        private var initialStartWith: Iterable<T>? = null
        private fun subscribeWithReconnect(observerHandle: ObserverHandle, onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit, startWithValues: Iterable<T>? = null) {
            var subscriptionError: Throwable?
            try {
                val subscription = initial.updates.let { if (startWithValues != null) it.startWith(startWithValues) else it }
                        .subscribe(onNext, observerHandle::fail, observerHandle::unsubscribe)
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
            return newObservable.reconnectingSubscriber.subscribeWithReconnect(observerHandle, onNext, onStop, onDisconnect, onReconnect)
        }

        override fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle {
            val observerNotifier = ObserverHandle()
            // TODO - change the establish connection method to be non-blocking
            observersPool.execute {
                subscribeWithReconnect(observerNotifier, onNext, onStop, onDisconnect, onReconnect, initialStartWith)
            }
            return observerNotifier
        }

        override fun startWithValues(values: Iterable<T>): ReconnectingObservable<T> {
            initialStartWith = values
            return this
        }

    }

}