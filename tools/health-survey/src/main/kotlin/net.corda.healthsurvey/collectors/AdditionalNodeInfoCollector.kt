package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class AdditionalNodeInfoCollector(private val additionalNodeInfos: Path) : TrackedCollector("Collecting additional node information files ...") {

    override fun collect(report: Report) {
        if (Files.exists(additionalNodeInfos)) {
            val paths = Files.list(additionalNodeInfos)
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
