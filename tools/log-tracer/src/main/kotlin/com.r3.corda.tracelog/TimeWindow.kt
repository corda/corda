package com.r3.corda.tracelog

data class TimeWindow(val start: Timestamp, val end: Timestamp) {
    fun padded(byMicroseconds: Long): Pair<Long, Long> = (start - byMicroseconds) to (end + byMicroseconds)

    companion object {
        val Int.seconds: Long
            get() = this * 1000L * 1000L
    }
}
