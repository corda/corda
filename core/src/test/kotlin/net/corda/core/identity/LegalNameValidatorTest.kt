package net.corda.core.identity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Test Legal  Name")
        }
        LegalNameValidator.validateLegalName(LegalNameValidator.normaliseLegalName("Test Legal  Name"))
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Test ")
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName(" Test")
        }
    }

    @Test
    fun `blacklisted words`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Test Server")
        }
    }

    @Test
    fun `blacklisted characters`() {
        LegalNameValidator.validateLegalName("Test")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("\$Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("\"Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("\'Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("=Test")
        }
    }

    @Test
    fun `unicode range`() {
        LegalNameValidator.validateLegalName("Test A")
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validateLegalName("Test Α")
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        LegalNameValidator.validateLegalName(longLegalName.toString())

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName(longLegalName.append("A").toString())
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        LegalNameValidator.validateLegalName("Good legal name")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("bad name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("bad Name")
        }
    }

    @Test
    fun `correctly handle whitespaces`() {
        assertEquals("Legal Name With Tab", LegalNameValidator.normaliseLegalName("Legal Name With\tTab"))
        assertEquals("Legal Name With Unicode Whitespaces", LegalNameValidator.normaliseLegalName("Legal Name\u2004With\u0009Unicode\u0020Whitespaces"))
        assertEquals("Legal Name With Line Breaks", LegalNameValidator.normaliseLegalName("Legal Name With\n\rLine\nBreaks"))
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Legal Name With\tTab")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Legal Name\u2004With\u0009Unicode\u0020Whitespaces")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateLegalName("Legal Name With\n\rLine\nBreaks")
        }
    }

    @Test
    fun `validate x500Name`() {
        CordaX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        CordaX500Name.parse("O=Bank A, L=New York, C=US, CN=Service Name")
        CordaX500Name.parse("O=Bank A, L=New York, C=US")
        CordaX500Name.parse("O=Bank A, L=New York, C=US")

        // Missing Organisation
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
        // Missing Locality
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, C=US, OU=Org Unit, CN=Service Name")
        }
        // Missing Country
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, L=New York, OU=Org Unit, CN=Service Name")
        }
        // Wrong organisation name format
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=B, L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
        // Wrong organisation name format
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=B, L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
    }
}