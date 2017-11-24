package net.corda.core.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Test Legal  Name")
        }
        LegalNameValidator.validateFullOrganization(LegalNameValidator.normalize("Test Legal  Name"))
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Test ")
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization(" Test")
        }
    }

    @Test
    fun `blacklisted words`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Test Server")
        }
    }

    @Test
    fun `blacklisted characters`() {
        LegalNameValidator.validateFullOrganization("Test")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("\$Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("\"Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("\'Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("=Test")
        }
    }

    @Test
    fun `unicode range`() {
        LegalNameValidator.validateFullOrganization("The quick brown fox jumped over the lazy dog.1234567890")
        assertFailsWith(IllegalArgumentException::class) {
            // Right to left direction override
            LegalNameValidator.validateFullOrganization("\u202EdtL 3R")
        }
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validateFullOrganization("Test \u0391")
        }
        // Latin capital letter turned m
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateFullOrganization( "Test\u019CLtd")
        }
        // Latin small letter turned e
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateFullOrganization("Test\u01ddLtd")
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        LegalNameValidator.validateFullOrganization(longLegalName.toString())

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization(longLegalName.append("A").toString())
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        LegalNameValidator.validateFullOrganization("Good legal name")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("bad name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("bad Name")
        }
    }

    @Test
    fun `correctly handle whitespaces`() {
        assertEquals("Legal Name With Tab", LegalNameValidator.normalize("Legal Name With\tTab"))
        assertEquals("Legal Name With Unicode Whitespaces", LegalNameValidator.normalize("Legal Name\u2004With\u0009Unicode\u0020Whitespaces"))
        assertEquals("Legal Name With Line Breaks", LegalNameValidator.normalize("Legal Name With\n\rLine\nBreaks"))
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Legal Name With\tTab")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Legal Name\u2004With\u0009Unicode\u0020Whitespaces")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateFullOrganization("Legal Name With\n\rLine\nBreaks")
        }
    }
}