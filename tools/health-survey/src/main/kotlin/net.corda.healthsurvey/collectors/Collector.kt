package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report

interface Collector {

    fun collect(report: Report)

}
