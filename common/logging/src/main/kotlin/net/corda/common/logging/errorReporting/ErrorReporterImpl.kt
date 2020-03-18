package net.corda.common.logging.errorReporting

import org.slf4j.Logger
import java.text.MessageFormat
import java.util.*

private const val MESSAGE_TEMPLATE = "errorTemplate"
private const val ERROR_BAR_RESOURCE = "ErrorBar"
private const val ERROR_CODE_MESSAGE = "errorCodeMessage"
private const val ERROR_CODE_URL = "errorCodeUrl"

internal class ErrorReporterImpl(private val resourceLocation: String,
                                 private val locale: Locale) : ErrorReporter {

    private fun formatCodeForLogs(error: ErrorCode) : String {
        return "${error.namespace}:${error.code}"
    }

    private fun formatCodeForResources(error: ErrorCode) : String {
        return "${error.namespace}-${error.code}"
    }

    private fun fetchAndFormat(resource: String, property: String, params: Array<out Any>) : String {
        val bundle = ResourceBundle.getBundle(resource, locale)
        val template = bundle.getString(property)
        val formatter = MessageFormat(template, locale)
        return formatter.format(params)
    }

    private fun messageForCode(error: ErrorCode) : String {
        val resource = "$resourceLocation/${formatCodeForResources(error)}"
        return fetchAndFormat(resource, MESSAGE_TEMPLATE, error.parameters.toTypedArray())
    }

    private fun constructErrorInfoBar(error: ErrorCode) : String {
        val resource = "$resourceLocation/$ERROR_BAR_RESOURCE"
        val codeMessage = fetchAndFormat(resource, ERROR_CODE_MESSAGE, arrayOf(formatCodeForLogs(error)))
        val urlMessage = fetchAndFormat(resource, ERROR_CODE_URL, arrayOf(locale.toLanguageTag(), formatCodeForLogs(error)))
        return "[$codeMessage, $urlMessage]"
    }

    override fun report(error: ErrorCode, logger: Logger) {
        val message = "${messageForCode(error)} ${constructErrorInfoBar(error)}"
        logger.error(message)
    }
}