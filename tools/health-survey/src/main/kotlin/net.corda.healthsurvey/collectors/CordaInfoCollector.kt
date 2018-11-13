package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.FileUtils.checksum
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

class CordaInfoCollector : TrackedCollector("Collecting information about Corda installation ...") {

    override fun collect(report: Report) {
        val paths = Files.list(Paths.get("."))
                .filter { path -> path.toString().contains("corda", true) && path.toString().endsWith(".jar", true) }
                .toList()
        val file = report.addFile("corda.txt")
        paths.forEach { jarPath ->
            val lastModified = Files.getLastModifiedTime(jarPath)
            val checksum = checksum(jarPath)
            file.withContent("$jarPath; last modified = $lastModified; checksum = $checksum\n")
        }
        if (paths.isEmpty()) {
            fail("Failed to find information about Corda installation")
        } else {
            complete("Collected information about Corda installation")
        }
    }

}
