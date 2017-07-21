package net.corda.core.contracts

import net.corda.core.internal.div
import net.corda.core.internal.until
import net.corda.core.serialization.CordaSerializable
import java.time.Duration
import java.time.Instant

/**
 * A time-window is required for validation/notarization purposes. If present in a transaction, contains a time that was
 * verified by the uniqueness service. The true time must be in the time interval `[fromTime, untilTime)`.
 *
 * Usually a time-window is required to have both sides defined. However some apps may require a time-window which is
 * open-ended on one of the two sides.
 */
@CordaSerializable
abstract class TimeWindow {
    companion object {
        /** Creates a [TimeWindow] with null [untilTime], i.e. the time interval `[fromTime, ∞]`. [midpoint] will return null. */
        @JvmStatic
        fun fromOnly(fromTime: Instant): TimeWindow = From(fromTime)

        /** Creates a [TimeWindow] with null [fromTime], i.e. the time interval `[∞, untilTime)`. [midpoint] will return null. */
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
     * `fromTime + (untilTime - fromTime)/2`, otherwise returns null.
     */
    abstract val midpoint: Instant?

    /** Returns true iff the given [instant] is within the time interval of this [TimeWindow]. */
    abstract operator fun contains(instant: Instant): Boolean

    private data class From(override val fromTime: Instant) : TimeWindow() {
        override val untilTime: Instant? get() = null
        override val midpoint: Instant? get() = null
        override fun contains(instant: Instant): Boolean = instant >= fromTime
        override fun toString(): String = "[$fromTime, ∞]"
    }

    private data class Until(override val untilTime: Instant) : TimeWindow() {
        override val fromTime: Instant? get() = null
        override val midpoint: Instant? get() = null
        override fun contains(instant: Instant): Boolean = instant < untilTime
        override fun toString(): String = "[∞, $untilTime)"
    }

    private data class Between(override val fromTime: Instant, override val untilTime: Instant) : TimeWindow() {
        init {
            require(fromTime < untilTime) { "fromTime must be earlier than untilTime" }
        }
        override val midpoint: Instant get() = fromTime + (fromTime until untilTime) / 2
        override fun contains(instant: Instant): Boolean = instant >= fromTime && instant < untilTime
        override fun toString(): String = "[$fromTime, $untilTime)"
    }
}