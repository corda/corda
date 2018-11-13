package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.FileUtils.checksum
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

class DriverCollector : TrackedCollector("Collecting driver information ...") {

    override fun collect(report: Report) {
        val driversPath = Paths.get("drivers")
        val file = report.addFile("drivers.txt")
        if (Files.exists(driversPath)) {
            val paths = Files.list(driversPath)
                    .filter { path -> path.toString().endsWith(".jar", true) }
                    .toList()
            paths.forEach { path ->
                val lastModified = Files.getLastModifiedTime(path)
                val checksum = checksum(path)
                file.withContent("$path; last modified = $lastModified; checksum = $checksum\n")
            }
        }
        complete("Collected driver information")
    }

}
