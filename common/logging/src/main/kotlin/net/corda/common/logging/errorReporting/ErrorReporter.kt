package net.corda.common.logging.errorReporting

import org.slf4j.Logger

/**
 * Reports error conditions to the logs, using localised error messages.
 */
internal interface ErrorReporter {
    fun report(error: ErrorCode, logger: Logger)
}