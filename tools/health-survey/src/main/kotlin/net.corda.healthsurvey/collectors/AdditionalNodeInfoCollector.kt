package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

class AdditionalNodeInfoCollector : TrackedCollector("Collecting additional node information files ...") {

    override fun collect(report: Report) {
        val directory = Paths.get("additional-node-infos")
        if (Files.exists(directory)) {
            val paths = Files.list(directory)
                    .filter { path -> path.toString().contains("nodeInfo-") }
                    .toList()
            val file = report.addFile("additional-node-infos.txt")
            paths.forEach { path ->
                val lastModified = Files.getLastModifiedTime(path)
                file.withContent("$path; last modified = $lastModified\n")
            }
            complete("Collected additional node information files")
        } else {
            fail("Failed to find additional node information files")
        }
    }

}
