package net.corda.common.logging.errorReporting

import org.slf4j.Logger
import java.util.Locale

/**
 * Report errors that have occurred.
 *
 * Doing this allows the error reporting framework to find the corresponding resources for the error and pick the correct locale.
 *
 * @param error The error that has occurred.
 */
fun Logger.report(error: ErrorCode<*>) = ErrorReporting().getReporter().report(error, this)

internal fun ErrorCode<*>.formatCode() : String {
    val namespaceString = this.code.namespace.lowercase(Locale.getDefault()).replace("_", "-")
    val codeString = this.code.toString().lowercase(Locale.getDefault()).replace("_", "-")
    return "$namespaceString-$codeString"
}