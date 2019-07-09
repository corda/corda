package net.corda.client.rpc.internal

import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import rx.Observable
import rx.Subscription
import java.util.concurrent.ExecutorService

class ReconnectingObservableImpl<T> private constructor(
        private val reconnectingSubscriber: ReconnectingSubscriber<T>
) : Observable<T>(reconnectingSubscriber), ReconnectingObservable<T> by reconnectingSubscriber {

    constructor(
            reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
            observersPool: ExecutorService,
            initial: DataFeed<*, T>,
            createDataFeed: () -> DataFeed<*, T>
    ) : this(ReconnectingSubscriber(reconnectingRPCConnection, observersPool, initial, createDataFeed))

    private class ReconnectingSubscriber<T>(private val reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
                                            private val observersPool: ExecutorService,
                                            val initial: DataFeed<*, T>,
                                            val createDataFeed: () -> DataFeed<*, T>): OnSubscribe<T>, ReconnectingObservable<T> {
        private companion object {
            private val log = contextLogger()
        }

        override fun call(subscriber: rx.Subscriber<in T>) {
            val handle = subscribe(subscriber::onNext, {}, {}, {})
            // This additional subscription allows us to detect un-subscription calls from clients and un-subscribe any subscription that
            // resulted from re-connections.
            subscriber.add(object: Subscription {
                @Volatile
                private var unsubscribed: Boolean = false

                override fun unsubscribe() {
                    handle.stop()
                    unsubscribed = true
                }

                override fun isUnsubscribed(): Boolean = unsubscribed
            })
        }

        private var initialStartWith: Iterable<T>? = null

        private fun subscribeWithReconnect(
                observerHandle: ObserverHandle,
                onNext: (T) -> Unit,
                onStop: () -> Unit,
                onDisconnect: () -> Unit,
                onReconnect: () -> Unit,
                startWithValues: Iterable<T>? = null
        ) {
            val subscriptionError = try {
                val subscription = initial.updates
                        .let { if (startWithValues != null) it.startWith(startWithValues) else it }
                        .subscribe(onNext, observerHandle::fail, observerHandle::stop)
                observerHandle.await().apply {
                    subscription.unsubscribe()
                }
            } catch (e: Exception) {
                log.error("Failed to register subscriber .", e)
                e
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