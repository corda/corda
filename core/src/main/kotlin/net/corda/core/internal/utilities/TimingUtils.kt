package net.corda.core.internal.utilities

import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

fun measureMilliAndNanoTime(block: () -> Unit): Double {
    return measureNanoTime(block).toDouble() / TimeUnit.MILLISECONDS.toNanos(1).toDouble()
}