package net.corda.core.internal

import java.lang.Character.UnicodeScript.*
import java.text.Normalizer
import java.util.regex.Pattern
import javax.security.auth.x500.X500Principal

object LegalNameValidator {
    @Deprecated("Use validateOrganization instead", replaceWith = ReplaceWith("validateOrganization(normalizedLegalName)"))
    fun validateLegalName(normalizedLegalName: String) = validateOrganization(normalizedLegalName)

    /**
     * The validation function validates a string for use as part of a legal name. It applies the following rules:
     *
     * - No blacklisted words like "node", "server".
     * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce
     *   names over the phone, and character confusability attacks.
     * - No commas or equals signs.
     * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
     *
     * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
     */
    fun validateNameAttribute(normalizedNameAttribute: String) {
        Rule.baseNameRules.forEach { it.validate(normalizedNameAttribute) }
    }

    /**
     * The validation function validates a string for use as the organization attribute of a name, which includes additional
     * constraints over basic name attribute checks. It applies the following rules:
     *
     * - No blacklisted words like "node", "server".
     * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce
     *   names over the phone, and character confusability attacks.
     * - Must consist of at least three letters and should start with a capital letter.
     * - No commas or equals signs.
     * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
     *
     * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
     */
    fun validateOrganization(normalizedOrganization: String) {
        Rule.legalNameRules.forEach { it.validate(normalizedOrganization) }
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
            val baseNameRules: List<Rule<String>> = listOf(
                    UnicodeNormalizationRule(),
                    CharacterRule(',', '=', '$', '"', '\'', '\\'),
                    WordRule("node", "server"),
                    LengthRule(maxLength = 255),
                    // TODO: Implement confusable character detection if we add more scripts.
                    UnicodeRangeRule(Character.UnicodeBlock.BASIC_LATIN),
                    X500NameRule()
            )
            val legalNameRules: List<Rule<String>> = baseNameRules + listOf(
                    CapitalLetterRule(),
                    MustHaveAtLeastTwoLettersRule()
            )
        }

        abstract fun validate(legalName: T)

        private class UnicodeNormalizationRule : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName == normalize(legalName)) { "Legal name must be normalized. Please use 'normalize' to normalize the legal name before validation." }
            }
        }

        private class UnicodeRangeRule(vararg supportScripts: Character.UnicodeBlock) : Rule<String>() {
            val supportScriptsSet = supportScripts.toSet()

            override fun validate(legalName: String) {
                val illegalChars = legalName.toCharArray().filter { Character.UnicodeBlock.of(it) !in supportScriptsSet }.size
                // We don't expose the characters or the legal name, for security reasons
                require (illegalChars == 0) { "$illegalChars forbidden characters in legal name." }
            }
        }

        private class CharacterRule(vararg val bannedChars: Char) : Rule<String>() {
            override fun validate(legalName: String) {
                bannedChars.forEach {
                    require(!legalName.contains(it, true)) { "Character not allowed in legal names: $it" }
                }
            }
        }

        private class WordRule(vararg val bannedWords: String) : Rule<String>() {
            override fun validate(legalName: String) {
                bannedWords.forEach {
                    require(!legalName.contains(it, ignoreCase = true)) { "Word not allowed in legal names: $it" }
                }
            }
        }

        private class LengthRule(val maxLength: Int) : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName.length <= maxLength) { "Legal name longer then $maxLength characters." }
            }
        }

        private class CapitalLetterRule : Rule<String>() {
            override fun validate(legalName: String) {
                val capitalizedLegalName = legalName.capitalize()
                require(legalName == capitalizedLegalName) { "Legal name should be capitalized. i.e. '$capitalizedLegalName'" }
            }
        }

        private class X500NameRule : Rule<String>() {
            override fun validate(legalName: String) {
                // This will throw IllegalArgumentException if the name does not comply with X500 name format.
                X500Principal("CN=$legalName")
            }
        }

        private class MustHaveAtLeastTwoLettersRule : Rule<String>() {
            override fun validate(legalName: String) {
                // Try to exclude names like "/", "£", "X" etc.
                require(legalName.count { it.isLetter() } >= 2) { "Illegal input legal name '$legalName'. Legal name must have at least two letters" }
            }
        }
    }
}