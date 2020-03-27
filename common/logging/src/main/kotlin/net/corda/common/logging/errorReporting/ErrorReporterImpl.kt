package net.corda.common.logging.errorReporting

import org.slf4j.Logger
import java.text.MessageFormat
import java.util.*

internal const val ERROR_BAR_RESOURCE = "ErrorBar"
internal const val ERROR_CODE_MESSAGE = "errorCodeMessage"
internal const val ERROR_CODE_URL = "errorCodeUrl"

internal class ErrorReporterImpl(private val resourceLocation: String,
                                 private val locale: Locale,
                                 private val errorContextProvider: ErrorContextProvider) : ErrorReporter {

    private fun formatCodeForLogs(error: ErrorCode) : String {
        return "${error.namespace}-${error.code}"
    }

    private fun fetchAndFormat(resource: String, property: String, params: Array<out Any>) : String {
        val bundle = ResourceBundle.getBundle(resource, locale)
        val template = bundle.getString(property)
        val formatter = MessageFormat(template, locale)
        return formatter.format(params)
    }

    private fun constructErrorInfoBar(error: ErrorCode) : String {
        val resource = "$resourceLocation/$ERROR_BAR_RESOURCE"
        val codeMessage = fetchAndFormat(resource, ERROR_CODE_MESSAGE, arrayOf(formatCodeForLogs(error)))
        val urlMessage = fetchAndFormat(resource, ERROR_CODE_URL, arrayOf(errorContextProvider.getURL(locale)))
        return "[$codeMessage, $urlMessage]"
    }

    override fun report(error: ErrorCode, logger: Logger) {
        val errorResource = ErrorResource.fromErrorCode(error, resourceLocation, locale)
        val message = "${errorResource.errorMessage} ${constructErrorInfoBar(error)}"
        logger.error(message)
    }
}