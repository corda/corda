package net.corda.client.rpc.reconnect

import net.corda.core.CordaInternal
import net.corda.core.utilities.minutes
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Handle to externally control a subscribed observer for [ReconnectingObservable]s.
 */
class ObserverHandle {
    private val terminated = LinkedBlockingQueue<Optional<Throwable>>(1)

    /**
     * Unsubscribes the associated subscribed observer.
     */
    fun unsubscribe() = terminated.put(Optional.empty())

    @CordaInternal
    internal fun fail(e: Throwable) = terminated.put(Optional.of(e))

    /**
     * Returns null if the observation ended successfully.
     */
    @CordaInternal
    internal fun await(duration: Duration = 60.minutes): Throwable? = terminated.poll(duration.seconds, TimeUnit.SECONDS).orElse(null)
}