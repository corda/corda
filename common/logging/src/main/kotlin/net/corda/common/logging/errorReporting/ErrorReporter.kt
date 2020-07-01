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
     * @param messagePrefix An optional string that will be prepended to the message,
     * a trailing whitespace will be appended automatically if the prefix is defined.
     * @param messagePostfix An optional string that will be appended to the message,
     * a leading whitespace will be appended automatically if the postfix is defined.
     */
    fun report(error: ErrorCode<*>, logger: Logger, messagePrefix: String? = null, messagePostfix: String? = null)
}