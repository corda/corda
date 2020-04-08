package net.corda.common.logging.errorReporting

import org.slf4j.Logger

/**
 * Report errors that have occurred.
 *
 * Doing this allows the error reporting framework to find the corresponding resources for the error and pick the correct locale.
 *
 * @param error The error that has occurred.
 */
fun Logger.report(error: ErrorCode<*, *>) = ErrorReporting().getReporter().report(error, this)

internal fun ErrorCode<*, *>.formatCode() : String = "${this.namespace.toString().toLowerCase()}-${this.code.toString().toLowerCase()}"