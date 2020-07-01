package net.corda.common.logging.errorReporting

import org.slf4j.Logger

/**
 * Report errors that have occurred.
 *
 * Doing this allows the error reporting framework to find the corresponding resources for the error and pick the correct locale.
 *
 * @param error The error that has occurred.
 * @param messagePrefix An optional string that will be prepended to the message
 * @param messagePostfix An optional string that will be appended to the message
 */
fun Logger.report(error: ErrorCode<*>, messagePrefix: String? = null, messagePostfix: String? = null)
        = ErrorReporting().getReporter().report(error, this, messagePrefix, messagePostfix)

internal fun ErrorCode<*>.formatCode() : String {
    val namespaceString = this.code.namespace.toLowerCase().replace("_", "-")
    val codeString = this.code.toString().toLowerCase().replace("_", "-")
    return "$namespaceString-$codeString"
}