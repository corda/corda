package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.streams.toList

class OptInLogCollector : TrackedCollector("Collecting log files ...") {

    override fun collect(report: Report) {
        val directory = Paths.get("logs")
        if (Files.exists(directory)) {
            val paths = Files.list(directory)
                    // Only include log files
                    .filter { path -> path.toString().endsWith(".log", true) }
                    // Only include files that have been modified over the past 3 days
                    .filter { path -> Files.getLastModifiedTime(path).toInstant() > Instant.now().minusSeconds(3 * 24 * 60 * 60) }
                    .toList()
            paths.forEach { path ->
                report.addFile(path.fileName.toString()).asCopyOfFile(path)
            }
            complete("Collected log files")
        } else {
            fail("Failed to find log files")
        }
    }

}
