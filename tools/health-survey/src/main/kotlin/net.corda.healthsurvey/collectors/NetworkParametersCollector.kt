package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Path

class NetworkParametersCollector(private val networkParameters: Path) : TrackedCollector("Collecting network parameters ...") {

    override fun collect(report: Report) {
        if (Files.exists(networkParameters)) {
            report.addFile("network-parameters").asCopyOfFile(networkParameters)
            complete("Collected network parameters")
        } else {
            fail("Failed to find network parameters")
        }
    }

}
