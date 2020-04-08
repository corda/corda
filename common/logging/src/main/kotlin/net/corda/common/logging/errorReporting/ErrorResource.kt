package net.corda.common.logging.errorReporting

import java.text.MessageFormat
import java.util.*

/**
 * A representation of a single error resource file.
 *
 * This handles selecting the right properties from the resource bundle and formatting the error message.
 */
class ErrorResource private constructor(private val bundle: ResourceBundle,
                                        private val locale: Locale) {

    companion object {
        fun fromErrorCode(errorCode: ErrorCode<*, *>, resourceLocation: String, locale: Locale) : ErrorResource {
            val resource = "$resourceLocation/${errorCode.formatCode()}"
            val bundle = ResourceBundle.getBundle(resource, locale)
            return ErrorResource(bundle, locale)
        }

        fun fromLoader(resource: String, classLoader: ClassLoader, locale: Locale) : ErrorResource {
            val bundle = ResourceBundle.getBundle(resource, locale, classLoader)
            return ErrorResource(bundle, locale)
        }

        private const val MESSAGE_TEMPLATE = "errorTemplate"
        private const val SHORT_DESCRIPTION = "shortDescription"
        private const val ACTIONS_TO_FIX = "actionsToFix"
        private const val ALIASES = "aliases"
    }

    private fun getProperty(propertyName: String) : String = bundle.getString(propertyName)

    private fun formatTemplate(template: String, args: Array<Any>) : String {
        val formatter = MessageFormat(template, locale)
        return formatter.format(args)
    }

    fun getErrorMessage(args: Array<Any>): String {
        val template = getProperty(MESSAGE_TEMPLATE)
        return formatTemplate(template, args)
    }

    val shortDescription: String = getProperty(SHORT_DESCRIPTION)
    val actionsToFix: String = getProperty(ACTIONS_TO_FIX)
    val aliases: String = getProperty(ALIASES)
}