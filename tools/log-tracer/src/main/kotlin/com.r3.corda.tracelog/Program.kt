package com.r3.corda.tracelog

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import kotlin.system.exitProcess
import com.r3.corda.tracelog.TimeWindow.Companion.seconds

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: log-tracer <log-files>")
        exitProcess(1)
    }
    LogTracer.INSTANCE.apply {
        val timeWindow = findTimeWindow(args.map { Paths.get(it) })
        val (startTime, endTime) = timeWindow.padded(5.seconds)
        val traceId = "${UUID.randomUUID()}".substring(0, 8)

        println("Generating trace '$traceId' (${timeWindow.start.dateTime} - ${timeWindow.end.dateTime}):")
        println()

        val now = Instant.now()
        val rootSpan = buildSpan("$now - $traceId")
                .withStartTimestamp(startTime)
                .start()

        val logPaths = args.map { Paths.get(it).ensureExists() }
        val logProcessors = logPaths.map { it to LogProcessor(this, it, rootSpan, timeWindow) }.toMap()
        logProcessors.values.forEach { it.findSessions() }

        for (logPath in logPaths) {
            print(" - Tracing log file '$logPath' ... ")
            val logProcessor = logProcessors.getValue(logPath)
            val processor = logProcessor.start()
            println("DONE")
            processor.flowNames.forEach { println("    -> ${it.id} - ${it.name}") }
            println()
        }

        LogProcessor.finalizeSessions()
        rootSpan.finish(endTime)

        Thread.sleep(2000)
    }
}

private fun Path.ensureExists(): Path = this.apply {
    if (!Files.exists(this)) {
        System.err.println("Error: Log file not found '$this'")
        exitProcess(1)
    }
}

private fun findTimeWindow(logFiles: List<Path>): TimeWindow {
    var minTimestamp: Timestamp? = null
    var maxTimestamp: Timestamp? = null
    for (logFile in logFiles) {
        Files.lines(logFile).forEach { line ->
            val timestamp = TagExtractor.extractTimestamp(line) ?: return@forEach
            if (minTimestamp == null || minTimestamp!! > timestamp) {
                minTimestamp = timestamp
            }
            if (maxTimestamp == null || maxTimestamp!! < timestamp) {
                maxTimestamp = timestamp
            }
        }
    }
    if (minTimestamp == null || maxTimestamp == null) {
        println("Unable to find any timestamps in the provided log files:")
        println(logFiles.joinToString("\n - ", " - "))
        exitProcess(1)
    }
    return TimeWindow(minTimestamp!!, maxTimestamp!!)
}
