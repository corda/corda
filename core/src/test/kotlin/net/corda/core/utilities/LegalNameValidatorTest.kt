package net.corda.core.utilities

import org.junit.Test
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("Test Legal  Name")
        }
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("Test ")
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate(" Test")
        }
    }

    @Test
    fun `blacklisted words`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("Test Server")
        }
    }

    @Test
    fun `blacklisted characters`() {
        LegalNameValidator.validate("Test")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("\$Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("\"Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("\'Test")
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("=Test")
        }
    }

    @Test
    fun `unicode range`() {
        LegalNameValidator.validate("Test A")
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validate("Test Α")
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        LegalNameValidator.validate(longLegalName.toString())

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate(longLegalName.append("A").toString())
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        LegalNameValidator.validate("Good Legal Name")
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("bad name")
        }

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("Bad name")
        }

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validate("bad Name")
        }
    }
}