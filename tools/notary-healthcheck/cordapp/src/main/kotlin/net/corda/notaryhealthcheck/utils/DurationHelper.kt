package net.corda.notaryhealthcheck.utils

import java.time.Duration

private val secondsPerMinute  = 60L
private val secondsPerHour = secondsPerMinute * 60L
private val secondsPerDay = secondsPerHour * 24L

fun Duration.toHumanReadable() : String {
    val sec = this.seconds
    val days = sec / secondsPerDay
    val hours = (sec.rem(secondsPerDay)) / secondsPerHour
    val minutes = (sec.rem(secondsPerHour)) / secondsPerMinute
    val seconds = (sec.rem(secondsPerMinute))
    val millis = this.nano / 1000000

    return "${if(days > 0) "${days}d " else ""}${"$hours".padStart(2, '0')}:${"$minutes".padStart(2, '0')}:${"$seconds".padStart(2, '0')}.${"$millis".padStart(3, '0')}"
}