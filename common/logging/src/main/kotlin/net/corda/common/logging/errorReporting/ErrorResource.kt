package net.corda.common.logging.errorReporting

import java.text.MessageFormat
import java.util.*

/**
 * A representation of a single error resource file.
 *
 * This handles selecting the right properties from the resource bundle and formatting the error message.
 */
class ErrorResource(private val resource: String,
                    private val locale: Locale,
                    private val parametersForTemplate: List<Any> = listOf(),
                    private val classLoader: ClassLoader? = null) {

    companion object {
        fun fromErrorCode(errorCode: ErrorCode, resourceLocation: String, locale: Locale) : ErrorResource {
            val resource = "$resourceLocation/${errorCode.formatCode()}"
            return ErrorResource(resource, locale, errorCode.parameters)
        }

        private const val MESSAGE_TEMPLATE = "errorTemplate"
        private const val SHORT_DESCRIPTION = "shortDescription"
        private const val ACTIONS_TO_FIX = "actionsToFix"
        private const val ALIASES = "aliases"
    }

    private fun getProperty(propertyName: String) : String {
        val bundle = if (classLoader != null) {
            ResourceBundle.getBundle(resource, locale, classLoader)
        } else {
            ResourceBundle.getBundle(resource, locale)
        }
        return bundle.getString(propertyName)
    }

    private fun formatTemplate(template: String) : String {
        val formatter = MessageFormat(template, locale)
        return formatter.format(parametersForTemplate.toTypedArray())
    }

    val errorMessage: String
        get() {
            val template = getProperty(MESSAGE_TEMPLATE)
            return formatTemplate(template)
        }

    val shortDescription: String = getProperty(SHORT_DESCRIPTION)
    val actionsToFix: String = getProperty(ACTIONS_TO_FIX)
    val aliases: String = getProperty(ALIASES)
}