package com.r3.corda.tracelog

import java.time.Instant

data class Timestamp(
        private val timestampString: String,
        val dateTime: Instant = Instant.parse(timestampString.replace(",", ".")),
        val timestampInMicroseconds: Long = dateTime.toEpochMilli() * 1000
) {
    operator fun plus(other: Long): Long =timestampInMicroseconds + other
    operator fun minus(other: Long): Long = timestampInMicroseconds - other
    operator fun compareTo(other: Timestamp): Int = timestampInMicroseconds.compareTo(other.timestampInMicroseconds)
}
