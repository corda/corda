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
        private const val ERROR_CODE_PAGE = "error-codes.html"
    }

    override fun getURL(locale: Locale): String {
        return "${CordaVersion.docsLink}/$ERROR_CODE_PAGE"
    }
}