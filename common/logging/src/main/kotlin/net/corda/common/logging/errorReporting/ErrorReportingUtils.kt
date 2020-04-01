package net.corda.common.logging.errorReporting

import org.slf4j.Logger

fun Logger.report(error: ErrorCode) {
    val reporter = ErrorReporting.getReporter()
    reporter.report(error, this)
}