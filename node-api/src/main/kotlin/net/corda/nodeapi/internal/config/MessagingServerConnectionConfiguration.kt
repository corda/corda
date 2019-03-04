package net.corda.nodeapi.internal.config

import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import java.time.Duration

/**
 * Predefined connection configurations used by Artemis clients (currently used in the P2P messaging layer).
 * The enum names represent the approximate total duration of the failover (with exponential back-off). The formula used to calculate
 * this duration is as follows:
 *
 * totalFailoverDuration = SUM(k=0 to [reconnectAttempts]) of [retryInterval] * POW([retryIntervalMultiplier], k)
 *
 * Example calculation for [DEFAULT]:
 *
 * totalFailoverDuration = 5 + 5 * 1.5 + 5 * (1.5)^2 + 5 * (1.5)^3 + 5 * (1.5)^4 = ~66 seconds
 *
 * @param failoverOnInitialAttempt Determines whether failover is triggered if initial connection fails.
 * @param initialConnectAttempts The number of reconnect attempts if failover is enabled for initial connection. A value
 * of -1 represents infinite attempts.
 * @param reconnectAttempts The number of reconnect attempts for failover after initial connection is done. A value
 * of -1 represents infinite attempts.
 * @param retryInterval Duration between reconnect attempts.
 * @param retryIntervalMultiplier Value used in the reconnection back-off process.
 * @param maxRetryInterval Determines the maximum duration between reconnection attempts. Useful when using infinite retries.
 */
enum class MessagingServerConnectionConfiguration {

    DEFAULT {
        override fun failoverOnInitialAttempt(isHa: Boolean) = true
        override fun initialConnectAttempts(isHa: Boolean) = 5
        override fun reconnectAttempts(isHa: Boolean) = 5
        override fun retryInterval() = 5.seconds
        override fun retryIntervalMultiplier() = 1.5
        override fun maxRetryInterval(isHa: Boolean) = 3.minutes
    },

    FAIL_FAST {
        override fun failoverOnInitialAttempt(isHa: Boolean) = isHa
        override fun initialConnectAttempts(isHa: Boolean) = 0
        // Client die too fast during failover/failback, need a few reconnect attempts to allow new master to become active
        override fun reconnectAttempts(isHa: Boolean) = if (isHa) 3 else 0
        override fun retryInterval() = 5.seconds
        override fun retryIntervalMultiplier() = 1.5
        override fun maxRetryInterval(isHa: Boolean) = 3.minutes
    },

    CONTINUOUS_RETRY {
        override fun failoverOnInitialAttempt(isHa: Boolean) = true
        override fun initialConnectAttempts(isHa: Boolean) = if (isHa) 0 else -1
        override fun reconnectAttempts(isHa: Boolean) = -1
        override fun retryInterval() = 5.seconds
        override fun retryIntervalMultiplier() = 1.5
        override fun maxRetryInterval(isHa: Boolean) = if (isHa) 3.minutes else 5.minutes
    };

    abstract fun failoverOnInitialAttempt(isHa: Boolean): Boolean
    abstract fun initialConnectAttempts(isHa: Boolean): Int
    abstract fun reconnectAttempts(isHa: Boolean): Int
    abstract fun retryInterval(): Duration
    abstract fun retryIntervalMultiplier(): Double
    abstract fun maxRetryInterval(isHa: Boolean): Duration
}
