package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths

class NetworkParametersCollector : TrackedCollector("Collecting network parameters ...") {

    override fun collect(report: Report) {
        val path = Paths.get("network-parameters")
        if (Files.exists(path)) {
            report.addFile("network-parameters").asCopyOfFile()
            complete("Collected network parameters")
        } else {
            fail("Failed to find network parameters")
        }
    }

}
