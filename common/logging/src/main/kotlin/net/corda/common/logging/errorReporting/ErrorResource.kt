package net.corda.common.logging.errorReporting

import net.corda.common.logging.errorReporting.ResourceBundleProperties.ACTIONS_TO_FIX
import net.corda.common.logging.errorReporting.ResourceBundleProperties.ALIASES
import net.corda.common.logging.errorReporting.ResourceBundleProperties.MESSAGE_TEMPLATE
import net.corda.common.logging.errorReporting.ResourceBundleProperties.SHORT_DESCRIPTION
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
        /**
         * Construct an error resource from a provided code.
         *
         * @param errorCode The code to get the resource bundle for
         * @param resourceLocation The location in the JAR of the error code resource bundles
         * @param locale The locale to use for this resource
         */
        fun fromErrorCode(errorCode: ErrorCode<*>, resourceLocation: String, locale: Locale) : ErrorResource {
            val resource = "$resourceLocation/${errorCode.formatCode()}"
            val bundle = ResourceBundle.getBundle(resource, locale)
            return ErrorResource(bundle, locale)
        }

        /**
         * Construct an error resource using resources loaded in a given classloader
         *
         * @param resource The resource bundle to load
         * @param classLoader The classloader used to load the resource bundles
         * @param locale The locale to use for this resource
         */
        fun fromLoader(resource: String, classLoader: ClassLoader, locale: Locale) : ErrorResource {
            val bundle = ResourceBundle.getBundle(resource, locale, classLoader)
            return ErrorResource(bundle, locale)
        }
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