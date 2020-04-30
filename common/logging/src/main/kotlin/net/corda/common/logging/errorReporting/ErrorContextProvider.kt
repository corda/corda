package net.corda.common.logging.errorReporting

import java.util.*

/**
 * Provide context around reported errors by supplying product specific information.
 */
interface ErrorContextProvider {

    /**
     * Get the URL to the docs site where the error codes are hosted.
     *
     * Note that the correct docs site link is likely to depend on the following:
     *  - The locale of the error message
     *  - The product the error was reported from
     *  - The version of the product the error was reported from
     *
     *  The returned URL must be the link the to the error code table in the documentation.
     *
     *  @param locale The locale of the link
     *  @return The URL of the docs site, to be printed in the logs
     */
    fun getURL(locale: Locale) : String
}