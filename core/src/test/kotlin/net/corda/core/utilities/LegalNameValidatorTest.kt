package net.corda.core.utilities

import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Test Legal  Name")
        }
        validateLegalName(normaliseLegalName("Test Legal  Name"))
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Test ")
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName(" Test")
        }
    }

    @Test
    fun `blacklisted words`() {
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Test Server")
        }
    }

    @Test
    fun `blacklisted characters`() {
        validateLegalName("Test")
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("\$Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("\"Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("\'Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("=Test")
        }
    }

    @Test
    fun `unicode range`() {
        validateLegalName("Test A")
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            validateLegalName("Test Α")
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        validateLegalName(longLegalName.toString())

        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName(longLegalName.append("A").toString())
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        validateLegalName("Good legal name")
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("bad name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("bad Name")
        }
    }

    @Test
    fun `correctly handle whitespaces`() {
        assertEquals("Legal Name With Tab", normaliseLegalName("Legal Name With\tTab"))
        assertEquals("Legal Name With Unicode Whitespaces", normaliseLegalName("Legal Name\u2004With\u0009Unicode\u0020Whitespaces"))
        assertEquals("Legal Name With Line Breaks", normaliseLegalName("Legal Name With\n\rLine\nBreaks"))
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Legal Name With\tTab")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Legal Name\u2004With\u0009Unicode\u0020Whitespaces")
        }
        assertFailsWith(IllegalArgumentException::class) {
            validateLegalName("Legal Name With\n\rLine\nBreaks")
        }
    }

    @Test
    fun `validate x500Name`() {
        validateX500Name(X500Name("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name"))
        validateX500Name(X500Name("O=Bank A, L=New York, C=US, CN=Service Name"))
        validateX500Name(X500Name("O=Bank A, L=New York, C=US"))
        validateX500Name(X500Name("O=Bank A, L=New York, C=US"))

        // Missing Organisation
        assertFailsWith(IllegalArgumentException::class) {
            validateX500Name(X500Name("L=New York, C=US, OU=Org Unit, CN=Service Name"))
        }
        // Missing Locality
        assertFailsWith(IllegalArgumentException::class) {
            validateX500Name(X500Name("O=Bank A, C=US, OU=Org Unit, CN=Service Name"))
        }
        // Missing Country
        assertFailsWith(IllegalArgumentException::class) {
            validateX500Name(X500Name("O=Bank A, L=New York, OU=Org Unit, CN=Service Name"))
        }
        // Wrong organisation name format
        assertFailsWith(IllegalArgumentException::class) {
            validateX500Name(X500Name("O=B, L=New York, C=US, OU=Org Unit, CN=Service Name"))
        }
        // Wrong organisation name format
        assertFailsWith(IllegalArgumentException::class) {
            validateX500Name(X500Name("O=B, L=New York, C=US, OU=Org Unit, CN=Service Name"))
        }
    }
}