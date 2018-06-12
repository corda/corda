/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.internal.div
import net.corda.core.internal.until
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.WireTransaction
import java.time.Duration
import java.time.Instant

/**
 * An interval on the time-line; not a single instantaneous point.
 *
 * There is no such thing as _exact_ time in networked systems due to the underlying physics involved and other issues
 * such as network latency. The best that can be approximated is "fuzzy time" or an instant of time which has margin of
 * tolerance around it. This is what [TimeWindow] represents. Time windows can be open-ended (i.e. specify only one of
 * [fromTime] and [untilTime]) or they can be fully bounded.
 *
 * [WireTransaction] has an optional time-window property, which if specified, restricts the validity of the transaction
 * to that time-interval as the Consensus Service will not sign it if it's received outside of this window.
 */
@CordaSerializable
abstract class TimeWindow {
    companion object {
        /** Creates a [TimeWindow] with null [untilTime], i.e. the time interval `[fromTime, ∞)`. [midpoint] will return null. */
        @JvmStatic
        fun fromOnly(fromTime: Instant): TimeWindow = From(fromTime)

        /** Creates a [TimeWindow] with null [fromTime], i.e. the time interval `(∞, untilTime)`. [midpoint] will return null. */
        @JvmStatic
        fun untilOnly(untilTime: Instant): TimeWindow = Until(untilTime)

        /**
         * Creates a [TimeWindow] with the time interval `[fromTime, untilTime)`. [midpoint] will return
         * `fromTime + (untilTime - fromTime) / 2`.
         * @throws IllegalArgumentException If [fromTime] ≥ [untilTime]
         */
        @JvmStatic
        fun between(fromTime: Instant, untilTime: Instant): TimeWindow = Between(fromTime, untilTime)

        /**
         * Creates a [TimeWindow] with the time interval `[fromTime, fromTime + duration)`. [midpoint] will return
         * `fromTime + duration / 2`
         */
        @JvmStatic
        fun fromStartAndDuration(fromTime: Instant, duration: Duration): TimeWindow = between(fromTime, fromTime + duration)

        /**
         * Creates a [TimeWindow] which is centered around [instant] with the given [tolerance] on both sides, i.e the
         * time interval `[instant - tolerance, instant + tolerance)`. [midpoint] will return [instant].
         */
        @JvmStatic
        fun withTolerance(instant: Instant, tolerance: Duration) = between(instant - tolerance, instant + tolerance)
    }

    /** Returns the inclusive lower-bound of this [TimeWindow]'s interval, with null implying infinity. */
    abstract val fromTime: Instant?

    /** Returns the exclusive upper-bound of this [TimeWindow]'s interval, with null implying infinity. */
    abstract val untilTime: Instant?

    /**
     * Returns the midpoint of [fromTime] and [untilTime] if both are non-null, calculated as
     * `fromTime + (untilTime - fromTime) / 2`, otherwise returns null.
     */
    abstract val midpoint: Instant?

    /**
     * Returns the duration between [fromTime] and [untilTime] if both are non-null. Otherwise returns null.
     */
    val length: Duration? get() {
        return if (fromTime == null || untilTime == null) {
            null
        } else {
            Duration.between(fromTime, untilTime)
        }
    }

    /** Returns true iff the given [instant] is within the time interval of this [TimeWindow]. */
    abstract operator fun contains(instant: Instant): Boolean

    @KeepForDJVM
    private data class From(override val fromTime: Instant) : TimeWindow() {
        override val untilTime: Instant? get() = null
        override val midpoint: Instant? get() = null
        override fun contains(instant: Instant): Boolean = instant >= fromTime
        override fun toString(): String = "[$fromTime, ∞)"
    }

    @KeepForDJVM
    private data class Until(override val untilTime: Instant) : TimeWindow() {
        override val fromTime: Instant? get() = null
        override val midpoint: Instant? get() = null
        override fun contains(instant: Instant): Boolean = instant < untilTime
        override fun toString(): String = "(∞, $untilTime)"
    }

    @KeepForDJVM
    private data class Between(override val fromTime: Instant, override val untilTime: Instant) : TimeWindow() {
        init {
            require(fromTime < untilTime) { "fromTime must be earlier than untilTime" }
        }

        override val midpoint: Instant get() = fromTime + (fromTime until untilTime) / 2
        override fun contains(instant: Instant): Boolean = instant >= fromTime && instant < untilTime
        override fun toString(): String = "[$fromTime, $untilTime)"
    }
}