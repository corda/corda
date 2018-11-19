package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Path

class NodeInfoCollector(private val baseDirectory: Path) : TrackedCollector("Collecting node information file ...") {

    override fun collect(report: Report) {
        val path = Files.list(baseDirectory)
                .filter { path -> path.toString().contains("nodeInfo-") }
                .findFirst()
        if (path.isPresent) {
            val nodeInfo = path.get()
            report.addFile(nodeInfo.fileName.toString()).asCopyOfFile(nodeInfo)
            complete("Collected node information file")
        } else {
            fail("Failed to find node information file")
        }
    }

}
