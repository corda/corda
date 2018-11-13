package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.FileUtils.checksum
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

class CorDappCollector : TrackedCollector("Collecting CorDapp information ...") {

    override fun collect(report: Report) {
        val cordappPath = Paths.get("cordapps")
        if (Files.exists(cordappPath)) {
            val paths = Files.list(cordappPath)
                    .filter { path -> path.toString().endsWith(".jar", true) }
                    .toList()
            val file = report.addFile("cordapps.txt")
            paths.forEach { path ->
                val lastModified = Files.getLastModifiedTime(path)
                val checksum = checksum(path)
                file.withContent("$path; last modified = $lastModified; checksum = $checksum\n")
            }
            complete("Collected CorDapp information")
        } else {
            fail("Failed to find CorDapp directory")
        }
    }

}
