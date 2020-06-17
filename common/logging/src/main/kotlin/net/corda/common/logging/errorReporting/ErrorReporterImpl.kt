package net.corda.common.logging.errorReporting

import org.slf4j.Logger
import java.lang.Exception
import java.text.MessageFormat
import java.util.*

internal const val ERROR_INFO_RESOURCE = "ErrorInfo"
internal const val ERROR_CODE_MESSAGE = "errorCodeMessage"
internal const val ERROR_CODE_URL = "errorCodeUrl"

internal class ErrorReporterImpl(private val resourceLocation: String,
                                 private val locale: Locale,
                                 private val errorContextProvider: ErrorContextProvider) : ErrorReporter {

    private fun fetchAndFormat(resource: String, property: String, params: Array<out Any>) : String {
        val bundle = ResourceBundle.getBundle(resource, locale)
        val template = bundle.getString(property)
        val formatter = MessageFormat(template, locale)
        return formatter.format(params)
    }

    // Returns the string appended to all reported errors, indicating the error code and the URL to go to.
    // e.g. [Code: my-error-code, For further information, please go to https://docs.corda.net/corda-os/4.5/error-codes.html]
    private fun getErrorInfo(error: ErrorCode<*>) : String {
        val resource = "$resourceLocation/$ERROR_INFO_RESOURCE"
        val codeMessage = fetchAndFormat(resource, ERROR_CODE_MESSAGE, arrayOf(error.formatCode()))
        val urlMessage = fetchAndFormat(resource, ERROR_CODE_URL, arrayOf(errorContextProvider.getURL(locale)))
        return "[$codeMessage $urlMessage]"
    }

    override fun report(error: ErrorCode<*>, logger: Logger) {
        val errorResource = ErrorResource.fromErrorCode(error, resourceLocation, locale)
        val message = "${errorResource.getErrorMessage(error.parameters.toTypedArray())} ${getErrorInfo(error)}"
        if (error is Exception) {
            logger.error(message, error)
        } else {
            logger.error(message)
        }
    }
}