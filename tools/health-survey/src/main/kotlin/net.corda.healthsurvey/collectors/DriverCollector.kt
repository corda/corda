package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.FileUtils.checksum
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class DriverCollector(private val drivers: Path) : TrackedCollector("Collecting driver information ...") {

    override fun collect(report: Report) {
        val file = report.addFile("drivers.txt")
        if (Files.exists(drivers)) {
            val paths = Files.list(drivers)
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
