package com.r3.corda.tracelog

import com.r3.corda.tracelog.TimeWindow.Companion.seconds
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.system.exitProcess

class TestSource {
    // TODO: turn into an actual test; currently used for debugging without having to build the tool
    @Test fun f() {
        val logPath = Paths.get(javaClass.classLoader.getResource("details.log").toURI())
        val tracer = LogTracer.INSTANCE.apply {
            val now = Instant.now()
            val timeWindow = findTimeWindow(listOf(logPath))
            val (startTime, endTime) = timeWindow.padded(5.seconds)
            val rootSpan = buildSpan("$now")
                    .withStartTimestamp(startTime)
                    .start()

            val processor = LogProcessor(this, logPath, rootSpan, findTimeWindow(listOf(logPath)))
            processor.findSessions()
            processor.start()

            rootSpan.finish(endTime)
            Thread.sleep(2000)
        }
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