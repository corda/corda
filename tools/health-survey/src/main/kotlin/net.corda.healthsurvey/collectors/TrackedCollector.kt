package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import net.corda.healthsurvey.tracking.TrackedConsoleTask

abstract class TrackedCollector(private val initialDescription: String? = null) : TrackedConsoleTask(), Collector {

    private lateinit var report: Report

    protected var errors: Int = 0

    fun start(report: Report) {
        this.report = report
        start()
    }

    override fun run() {
        try {
            initialDescription?.let { description ->
                step(description)
                Thread.sleep(500)
            }
            collect(report)
        } catch (exception: Exception) {
            fail(exception.message ?: exception.javaClass.simpleName)
        }
    }

    protected fun attempt(
            progressDescription: String,
            failedDescription: String,
            file: Report.ReportFile? = null,
            action: () -> Unit
    ) {
        step(progressDescription)
        try {
            Thread.sleep(500)
            action()
            Thread.sleep(500)
        } catch (exception: Exception) {
            file?.withContent("$failedDescription; $exception\n")
            fail(failedDescription)
            errors += 1
            Thread.sleep(1500)
        }
    }


}
