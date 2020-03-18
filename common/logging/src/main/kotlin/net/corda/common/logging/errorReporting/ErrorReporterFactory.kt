package net.corda.common.logging.errorReporting

import java.util.*

object ErrorReporterFactory {

    private var locale: Locale? = null
    private var resourceLocation: String? = null

    fun setParams(localeString: String, location: String) {
        locale = Locale.forLanguageTag(localeString)
        resourceLocation = location
    }

    internal fun getReporter() : ErrorReporter {
        val (localeToUse, resourceLocationToUse) = Pair(locale, resourceLocation)
        return if (localeToUse == null || resourceLocationToUse == null) {
            UnsetErrorReporter()
        } else {
            ErrorReporterImpl(resourceLocationToUse, localeToUse)
        }
    }
}