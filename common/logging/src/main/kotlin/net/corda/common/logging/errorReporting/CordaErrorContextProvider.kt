package net.corda.common.logging.errorReporting

import net.corda.common.logging.CordaVersion
import java.util.*

/**
 * Provides information specific to Corda to the error reporting library.
 *
 * The primary use of this is to provide the URL to the docs site where the error information is hosted.
 */
class CordaErrorContextProvider : ErrorContextProvider {

    companion object {
        private const val BASE_URL = "https://docs.corda.net/docs"
        private const val OS_PAGES = "corda-os"
        private const val ENTERPRISE_PAGES = "corda-enterprise"
        private const val ERROR_CODE_PAGE = "error-codes.html"
    }

    override fun getURL(locale: Locale): String {
        val versionNumber = CordaVersion.releaseVersion

        // This slightly strange block here allows the code to be merged across to Enterprise with no changes.
        val productVersion = if (CordaVersion.platformEditionCode == "OS") {
            OS_PAGES
        } else {
            ENTERPRISE_PAGES
        }
        return "$BASE_URL/$productVersion/$versionNumber/$ERROR_CODE_PAGE"
    }
}