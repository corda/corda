/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal.performance

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * [Rate] holds a quantity denoting the frequency of some event e.g. 100 times per second or 2 times per day.
 */
data class Rate(
        val numberOfEvents: Long,
        val perTimeUnit: TimeUnit
) {
    /**
     * Returns the interval between two subsequent events.
     */
    fun toInterval(): Duration {
        return Duration.of(TimeUnit.NANOSECONDS.convert(1, perTimeUnit) / numberOfEvents, ChronoUnit.NANOS)
    }

    /**
     * Converts the number of events to the given unit.
     */
    operator fun times(inUnit: TimeUnit): Long = inUnit.convert(numberOfEvents, perTimeUnit)

    override fun toString(): String = "$numberOfEvents / ${perTimeUnit.name.dropLast(1).toLowerCase()}"  // drop the "s" at the end
}

operator fun Long.div(timeUnit: TimeUnit) = Rate(this, timeUnit)
