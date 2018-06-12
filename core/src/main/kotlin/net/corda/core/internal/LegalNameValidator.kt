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

import net.corda.core.KeepForDJVM
import net.corda.core.internal.LegalNameValidator.normalize
import java.text.Normalizer
import javax.security.auth.x500.X500Principal

object LegalNameValidator {
    enum class Validation {
        MINIMAL,
        FULL
    }

    @Deprecated("Use validateOrganization instead", replaceWith = ReplaceWith("validateOrganization(normalizedLegalName)"))
    fun validateLegalName(normalizedLegalName: String) = validateOrganization(normalizedLegalName, Validation.FULL)

    /**
     * The validation function validates a string for use as part of a legal name. It applies the following rules:
     *
     * - Does not contain the null character
     * - Must be normalized (as per the [normalize] function).
     * - Length must be 255 characters or shorter.
     *
     * Full validation (typically this is only done for names the Doorman approves) adds:
     *
     * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce
     *   names over the phone, and character confusability attacks.
     * - No commas or equals signs.
     * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
     *
     * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
     */
    fun validateNameAttribute(normalizedNameAttribute: String, validation: Validation) {
        when (validation) {
            Validation.MINIMAL -> Rule.attributeRules.forEach { it.validate(normalizedNameAttribute) }
            Validation.FULL -> Rule.attributeFullRules.forEach { it.validate(normalizedNameAttribute) }
        }
    }

    /**
     * The validation function validates a string for use as the organization attribute of a name, which includes additional
     * constraints over basic name attribute checks. It applies the following additional rules:
     *
     * - Must be normalized (as per the [normalize] function).
     * - Length must be 255 characters or shorter.
     * - No blacklisted words like "node", "server".
     * - Must consist of at least three letters.
     *
     * Full validation (typically this is only done for names the Doorman approves) adds:
     *
     * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce
     *   names over the phone, and character confusability attacks.
     * - Must start with a capital letter.
     * - No commas or equals signs.
     * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
     *
     * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
     */
    fun validateOrganization(normalizedOrganization: String, validation: Validation) {
        when (validation) {
            Validation.MINIMAL -> Rule.legalNameRules.forEach { it.validate(normalizedOrganization) }
            Validation.FULL -> Rule.legalNameFullRules.forEach { it.validate(normalizedOrganization) }
        }
    }

    @Deprecated("Use normalize instead", replaceWith = ReplaceWith("normalize(legalName)"))
    fun normalizeLegalName(legalName: String): String = normalize(legalName)

    /**
     * The normalize function will trim the input string, replace any multiple spaces with a single space,
     * and normalize the string according to NFKC normalization form.
     */
    fun normalize(nameAttribute: String): String {
        val trimmedLegalName = nameAttribute.trim().replace(WHITESPACE, " ")
        return Normalizer.normalize(trimmedLegalName, Normalizer.Form.NFKC)
    }

    val WHITESPACE = "\\s++".toRegex()

    sealed class Rule<in T> {
        companion object {
            val attributeRules: List<Rule<String>> = listOf(
                    UnicodeNormalizationRule(),
                    LengthRule(maxLength = 255),
                    MustHaveAtLeastTwoLettersRule(),
                    CharacterRule('\u0000') // Ban null
            )
            val attributeFullRules: List<Rule<String>> = attributeRules + listOf(
                    CharacterRule(',', '=', '$', '"', '\'', '\\'),
                    // TODO: Implement confusable character detection if we add more scripts.
                    UnicodeRangeRule(Character.UnicodeBlock.BASIC_LATIN),
                    CapitalLetterRule()
            )
            val legalNameRules: List<Rule<String>> = attributeRules + listOf(
                    // Removal of word restriction was requested in https://github.com/corda/corda/issues/2326
                    // WordRule("node", "server"),
                    X500NameRule()
            )
            val legalNameFullRules: List<Rule<String>> = legalNameRules + listOf(
                    CharacterRule(',', '=', '$', '"', '\'', '\\'),
                    // TODO: Implement confusable character detection if we add more scripts.
                    UnicodeRangeRule(Character.UnicodeBlock.BASIC_LATIN),
                    CapitalLetterRule()
            )
        }

        abstract fun validate(legalName: T)

        @KeepForDJVM
        private class UnicodeNormalizationRule : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName == normalize(legalName)) { "Legal name must be normalized. Please use 'normalize' to normalize the legal name before validation." }
            }
        }

        @KeepForDJVM
        private class UnicodeRangeRule(vararg supportScripts: Character.UnicodeBlock) : Rule<String>() {
            val supportScriptsSet = supportScripts.toSet()

            override fun validate(legalName: String) {
                val illegalChars = legalName.toCharArray().filter { Character.UnicodeBlock.of(it) !in supportScriptsSet }.size
                // We don't expose the characters or the legal name, for security reasons
                require (illegalChars == 0) { "$illegalChars forbidden characters in legal name." }
            }
        }

        @KeepForDJVM
        private class CharacterRule(vararg val bannedChars: Char) : Rule<String>() {
            override fun validate(legalName: String) {
                bannedChars.forEach {
                    require(!legalName.contains(it, true)) { "Character not allowed in legal names: $it" }
                }
            }
        }

        @KeepForDJVM
        private class WordRule(vararg val bannedWords: String) : Rule<String>() {
            override fun validate(legalName: String) {
                bannedWords.forEach {
                    require(!legalName.contains(it, ignoreCase = true)) { "Word not allowed in legal names: $it" }
                }
            }
        }

        @KeepForDJVM
        private class LengthRule(val maxLength: Int) : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName.length <= maxLength) { "Legal name longer then $maxLength characters." }
            }
        }

        @KeepForDJVM
        private class CapitalLetterRule : Rule<String>() {
            override fun validate(legalName: String) {
                val capitalizedLegalName = legalName.capitalize()
                require(legalName == capitalizedLegalName) { "Legal name should be capitalized. i.e. '$capitalizedLegalName'" }
            }
        }

        @KeepForDJVM
        private class X500NameRule : Rule<String>() {
            override fun validate(legalName: String) {
                // This will throw IllegalArgumentException if the name does not comply with X500 name format.
                X500Principal("CN=$legalName")
            }
        }

        @KeepForDJVM
        private class MustHaveAtLeastTwoLettersRule : Rule<String>() {
            override fun validate(legalName: String) {
                // Try to exclude names like "/", "£", "X" etc.
                require(legalName.count { it.isLetter() } >= 2) { "Illegal input legal name '$legalName'. Legal name must have at least two letters" }
            }
        }
    }
}