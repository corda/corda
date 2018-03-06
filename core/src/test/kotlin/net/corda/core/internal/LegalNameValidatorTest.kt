/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalNameValidatorTest {
    @Test
    fun `no double spaces`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Test Legal  Name", LegalNameValidator.Validation.FULL)
        }
        LegalNameValidator.validateOrganization(LegalNameValidator.normalize("Test Legal  Name"), LegalNameValidator.Validation.FULL)
    }

    @Test
    fun `no trailing white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Test ", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `no prefixed white space`() {
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization(" Test", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `blacklisted characters`() {
        LegalNameValidator.validateOrganization("Test", LegalNameValidator.Validation.FULL)
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\$Test", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\"Test", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("\'Test", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("=Test", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `unicode range in organization`() {
        LegalNameValidator.validateOrganization("The quick brown fox jumped over the lazy dog.1234567890", LegalNameValidator.Validation.FULL)
        assertFailsWith(IllegalArgumentException::class) {
            // Null
            LegalNameValidator.validateOrganization("\u0000R3 Null", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            // Right to left direction override
            LegalNameValidator.validateOrganization("\u202EdtL 3R", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validateOrganization("Test \u0391", LegalNameValidator.Validation.FULL)
        }
        // Latin capital letter turned m
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateOrganization( "Test\u019CLtd", LegalNameValidator.Validation.FULL)
        }
        // Latin small letter turned e
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateOrganization("Test\u01ddLtd", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `unicode range in general attributes`() {
        LegalNameValidator.validateNameAttribute("The quick brown fox jumped over the lazy dog.1234567890", LegalNameValidator.Validation.FULL)
        assertFailsWith(IllegalArgumentException::class) {
            // Right to left direction override
            LegalNameValidator.validateNameAttribute("\u202EdtL 3R", LegalNameValidator.Validation.FULL)
        }
        // Right to left direction override is okay with minimal validation though
        LegalNameValidator.validateNameAttribute("\u202EdtL 3R", LegalNameValidator.Validation.MINIMAL)
        assertFailsWith(IllegalArgumentException::class) {
            // Greek letter A.
            LegalNameValidator.validateNameAttribute("Test \u0391", LegalNameValidator.Validation.FULL)
        }
        // Latin capital letter turned m
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateNameAttribute( "Test\u019CLtd", LegalNameValidator.Validation.FULL)
        }
        // Latin small letter turned e
        assertFailsWith<IllegalArgumentException> {
            LegalNameValidator.validateNameAttribute("Test\u01ddLtd", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `legal name length less then 256 characters`() {
        val longLegalName = StringBuilder()
        while (longLegalName.length < 255) {
            longLegalName.append("A")
        }
        LegalNameValidator.validateOrganization(longLegalName.toString(), LegalNameValidator.Validation.FULL)

        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization(longLegalName.append("A").toString(), LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `legal name should be capitalized`() {
        LegalNameValidator.validateOrganization("Good legal name", LegalNameValidator.Validation.FULL)
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("bad name", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("bad Name", LegalNameValidator.Validation.FULL)
        }
    }

    @Test
    fun `correctly handle whitespaces`() {
        assertEquals("Legal Name With Tab", LegalNameValidator.normalize("Legal Name With\tTab"))
        assertEquals("Legal Name With Unicode Whitespaces", LegalNameValidator.normalize("Legal Name\u2004With\u0009Unicode\u0020Whitespaces"))
        assertEquals("Legal Name With Line Breaks", LegalNameValidator.normalize("Legal Name With\n\rLine\nBreaks"))
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name With\tTab", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name\u2004With\u0009Unicode\u0020Whitespaces", LegalNameValidator.Validation.FULL)
        }
        assertFailsWith(IllegalArgumentException::class) {
            LegalNameValidator.validateOrganization("Legal Name With\n\rLine\nBreaks", LegalNameValidator.Validation.FULL)
        }
    }
}