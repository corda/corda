package net.corda.commmon.logging.errorReporting

import net.corda.common.logging.CordaVersion
import net.corda.common.logging.errorReporting.CordaErrorContextProvider
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class CordaErrorContextProviderTest {

    @Test(timeout = 300_000)
    fun `check that correct URL is returned from context provider`() {
        val context = CordaErrorContextProvider()
        val version = CordaVersion.releaseVersion.substringBefore("-") // Remove SNAPSHOT if present
        val expectedURL = "https://docs.corda.net/docs/corda-os/$version/error-codes.html"
        // In this first release, there is only one localisation and the URL structure for future localisations is currently unknown. As
        // a result, the same URL is expected for all locales.
        assertEquals(expectedURL, context.getURL(Locale.getDefault()))
        assertEquals(expectedURL, context.getURL(Locale.forLanguageTag("es-ES")))
    }
}