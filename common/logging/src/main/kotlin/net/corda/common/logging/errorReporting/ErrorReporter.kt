package net.corda.common.logging.errorReporting

import org.slf4j.Logger

/**
 * Reports error conditions to the logs, using localised error messages.
 */
internal interface ErrorReporter {
    /**
     * Report a particular error condition
     *
     * @param error The error to report
     * @param logger The logger to use when reporting this error
     */
    fun report(error: ErrorCode<*>, logger: Logger)
}