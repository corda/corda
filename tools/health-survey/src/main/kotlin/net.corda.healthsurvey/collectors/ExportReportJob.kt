package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.cli.Console.yellow
import net.corda.healthsurvey.output.Report

class ExportReportJob : TrackedCollector("Exporting report ...") {

    override fun collect(report: Report) {
        report.export()
        complete("Exported report to ${yellow(report.path)}")
    }

}
