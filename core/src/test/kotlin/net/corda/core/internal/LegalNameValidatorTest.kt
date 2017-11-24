package net.corda.core.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Test Legal  Name")
        }
        LegalNameValidator.validateOrganization(LegalNameValidator.normalize("Test Legal  Name"))
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Test ")
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization(" Test")
        }
    }

    @Test
    fun `blacklisted words`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Test Server")
        }
    }

    @Test
    fun `blacklisted characters`() {
        LegalNameValidator.validateOrganization("Test")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\$Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\"Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\'Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("=Test")
        }
    }

    @Test
    fun `unicode range`() {
        LegalNameValidator.validateOrganization("The quick brown fox jumped over the lazy dog.1234567890")
        assertFailsWith(IllegalArgumentException::class) {
            // Right to left direction override
            LegalNameValidator.validateOrganization("\u202EdtL 3R")
        }
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validateOrganization("Test \u0391")
        }
        // Latin capital letter turned m
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateOrganization( "Test\u019CLtd")
        }
        // Latin small letter turned e
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateOrganization("Test\u01ddLtd")
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        LegalNameValidator.validateOrganization(longLegalName.toString())

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization(longLegalName.append("A").toString())
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        LegalNameValidator.validateOrganization("Good legal name")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("bad name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("bad Name")
        }
    }

    @Test
    fun `correctly handle whitespaces`() {
        assertEquals("Legal Name With Tab", LegalNameValidator.normalize("Legal Name With\tTab"))
        assertEquals("Legal Name With Unicode Whitespaces", LegalNameValidator.normalize("Legal Name\u2004With\u0009Unicode\u0020Whitespaces"))
        assertEquals("Legal Name With Line Breaks", LegalNameValidator.normalize("Legal Name With\n\rLine\nBreaks"))
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name With\tTab")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name\u2004With\u0009Unicode\u0020Whitespaces")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name With\n\rLine\nBreaks")
        }
    }
}