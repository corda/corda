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
enum class ExternalBrokerConnectionConfiguration(
        val failoverOnInitialAttempt: Boolean,
        val initialConnectAttempts: Int,
        val reconnectAttempts: Int,
        val retryInterval: Duration,
        val retryIntervalMultiplier: Double,
        val maxRetryInterval: Duration) {

    DEFAULT(
            failoverOnInitialAttempt = true,
            initialConnectAttempts = 5,
            reconnectAttempts = 5,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.5,
            maxRetryInterval = 3.minutes
    ),

    FAIL_FAST(
            failoverOnInitialAttempt = false,
            initialConnectAttempts = 0,
            reconnectAttempts = 0,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.5,
            maxRetryInterval = 3.minutes
    ),

    CONTINUOUS_RETRY(
            failoverOnInitialAttempt = true,
            initialConnectAttempts = -1,
            reconnectAttempts = -1,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.5,
            maxRetryInterval = 5.minutes
    ),

    FIVE_MINUTES(
            failoverOnInitialAttempt = true,
            initialConnectAttempts = 13,
            reconnectAttempts = 13,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.2,
            maxRetryInterval = 5.minutes
    ),

    TEN_MINUTES(
            failoverOnInitialAttempt = true,
            initialConnectAttempts = 17,
            reconnectAttempts = 17,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.2,
            maxRetryInterval = 5.minutes
    ),

    ONE_HOUR(
            failoverOnInitialAttempt = true,
            initialConnectAttempts = 17,
            reconnectAttempts = 17,
            retryInterval = 5.seconds,
            retryIntervalMultiplier = 1.5,
            maxRetryInterval = 10.minutes
    )
}
