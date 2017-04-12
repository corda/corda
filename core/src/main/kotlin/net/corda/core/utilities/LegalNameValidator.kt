@file:JvmName("LegalNameValidator")

package net.corda.core.utilities

import java.lang.Character.UnicodeScript.*
import java.text.Normalizer
import java.util.regex.Pattern
import javax.security.auth.x500.X500Principal

/**
 * The validation function will validate the input string using the following rules:
 * - No blacklisted words like "node", "server".
 * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce names over the phone, and character confusability attacks.
 * - Should start with a capital letter.
 * - No commas or equals signs.
 * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
 */
fun validateLegalName(normalizedLegalName: String) {
    rules.forEach { it.validate(normalizedLegalName) }
}

/**
 * The normalize function will trim the input string, replace any multiple spaces with a single space,
 * and normalize the string according to NFKC normalization form.
 */
fun normaliseLegalName(legalName: String): String {
    val trimmedLegalName = legalName.trim().replace(Regex("\\s+"), " ")
    return Normalizer.normalize(trimmedLegalName, Normalizer.Form.NFKC)
}

private val rules: List<Rule<String>> = listOf(
        UnicodeNormalizationRule(),
        CharacterRule(',', '=', '$', '"', '\'', '\\'),
        WordRule("node", "server"),
        LengthRule(maxLength = 255),
        // TODO: Implement confusable character detection if we add more scripts.
        UnicodeRangeRule(LATIN, COMMON, INHERITED),
        CapitalLetterRule(),
        X500NameRule()
)

private class UnicodeNormalizationRule : Rule<String> {
    override fun validate(legalName: String) {
        require(legalName == normaliseLegalName(legalName)) { "Legal name must be normalized. Please use 'normaliseLegalName' to normalize the legal name before validation." }
    }
}

private class UnicodeRangeRule(vararg supportScripts: Character.UnicodeScript) : Rule<String> {
    private val pattern = supportScripts.map { "\\p{Is$it}" }.joinToString(separator = "", prefix = "[", postfix = "]*").let { Pattern.compile(it) }

    override fun validate(legalName: String) {
        require(pattern.matcher(legalName).matches()) {
            val illegalChars = legalName.replace(pattern.toRegex(), "").toSet()
            if (illegalChars.size > 1) {
                "Illegal characters $illegalChars in \"$legalName\"."
            } else {
                "Illegal character $illegalChars in \"$legalName\"."
            }
        }
    }
}

private class CharacterRule(vararg val bannedChars: Char) : Rule<String> {
    override fun validate(legalName: String) {
        bannedChars.forEach {
            require(!legalName.contains(it, true)) { "Illegal character: $it" }
        }
    }
}

private class WordRule(vararg val bannedWords: String) : Rule<String> {
    override fun validate(legalName: String) {
        bannedWords.forEach {
            require(!legalName.contains(it, ignoreCase = true)) { "Illegal word: $it" }
        }
    }
}

private class LengthRule(val maxLength: Int) : Rule<String> {
    override fun validate(legalName: String) {
        require(legalName.length <= maxLength) { "Legal name longer then 255 characters." }
    }
}

private class CapitalLetterRule : Rule<String> {
    override fun validate(legalName: String) {
        val capitalizedLegalName = legalName.split(" ").map(String::capitalize).joinToString(" ")
        require(legalName == capitalizedLegalName) { "Legal name should be capitalized. i.e. '$capitalizedLegalName'" }
    }
}

private class X500NameRule : Rule<String> {
    override fun validate(legalName: String) {
        // This will throw IllegalArgumentException if the name does not comply with X500 name format.
        X500Principal("CN=$legalName")
    }
}

private interface Rule<in T> {
    fun validate(legalName: T)
}
