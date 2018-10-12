package net.corda.flowworker

import org.slf4j.Logger
import java.text.NumberFormat

fun Logger.logMemoryStats(stage: String) {

    fun Long.toKB(): Long  = this / 1024

    System.gc()
    System.gc()

    val nf = NumberFormat.getNumberInstance()

    val runtime = Runtime.getRuntime()
    val freeMemory = runtime.freeMemory().toKB()
    val totalMemory = runtime.totalMemory().toKB()
    val maxMemory = runtime.maxMemory().toKB()

    info("Memory stats @$stage - Used memory: ${nf.format(totalMemory - freeMemory)} KB, Total memory: ${nf.format(totalMemory)} KB, Max memory: ${nf.format(maxMemory)} KB")
}