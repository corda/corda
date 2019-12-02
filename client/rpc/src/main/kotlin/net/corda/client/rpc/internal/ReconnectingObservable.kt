package net.corda.client.rpc.internal

import net.corda.core.messaging.DataFeed
import rx.Observable
import rx.Subscriber
import rx.Subscription
import java.util.concurrent.atomic.AtomicReference

class ReconnectingObservable<T> private constructor(subscriber: ReconnectingSubscriber<T>) : Observable<T>(subscriber) {

    constructor(
            reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
            initialDataFeed: DataFeed<*, T>,
            createDataFeed: () -> DataFeed<*, T>
    ) : this(ReconnectingSubscriber(reconnectingRPCConnection, initialDataFeed, createDataFeed))

    private class ReconnectingSubscriber<T>(
            private val reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
            private val initialDataFeed: DataFeed<*, T>,
            private val createDataFeed: () -> DataFeed<*, T>
    ) : OnSubscribe<T>, Subscription {

        private val subscriber = AtomicReference<Subscriber<in T>>()
        @Volatile
        private var backingSubscription: Subscription? = null
        @Volatile
        private var unsubscribed = false

        override fun unsubscribe() {
            backingSubscription?.unsubscribe()
            unsubscribed = true
        }

        override fun isUnsubscribed(): Boolean = unsubscribed

        override fun call(subscriber: Subscriber<in T>) {
            if (this.subscriber.compareAndSet(null, subscriber)) {
                subscriber.add(this)
                subscribeImmediately(initialDataFeed)
            } else {
                subscriber.onError(IllegalStateException("Only a single subscriber is allowed"))
            }
        }

        private fun subscribeImmediately(dataFeed: DataFeed<*, T>) {
            if (unsubscribed) return
            val subscriber = checkNotNull(this.subscriber.get())
            try {
                val previousSubscription = backingSubscription
                backingSubscription = dataFeed.updates.subscribe(subscriber::onNext, ::scheduleResubscribe, subscriber::onCompleted)
                previousSubscription?.unsubscribe()
            } catch (e: Exception) {
                scheduleResubscribe(e)
            }
        }

        private fun scheduleResubscribe(error: Throwable) {
            if (unsubscribed) return
            reconnectingRPCConnection.observersPool.execute {
                if (unsubscribed || reconnectingRPCConnection.isClosed()) return@execute
                reconnectingRPCConnection.reconnectOnError(error)
                // It can take a while to reconnect so we might find that we've shutdown in in the meantime
                if (unsubscribed || reconnectingRPCConnection.isClosed()) return@execute
                val newDataFeed = createDataFeed()
                subscribeImmediately(newDataFeed)
            }
        }
    }
}