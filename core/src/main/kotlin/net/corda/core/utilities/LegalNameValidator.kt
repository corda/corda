@file:JvmName("LegalNameValidator")

package net.corda.core.utilities

import net.corda.core.crypto.commonName
import org.bouncycastle.asn1.x500.X500Name
import java.lang.Character.UnicodeScript.*
import java.text.Normalizer
import java.util.regex.Pattern
import javax.security.auth.x500.X500Principal

/**
 * The validation function will validate the input string using the following rules:
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
fun validateLegalName(normalizedLegalName: String) {
    legalNameRules.forEach { it.validate(normalizedLegalName) }
}

// TODO: Implement X500 attribute validation once the specification has been finalised.
fun validateX500Name(x500Name: X500Name) {
    validateLegalName(x500Name.commonName)
}

val WHITESPACE = "\\s++".toRegex()

/**
 * The normalize function will trim the input string, replace any multiple spaces with a single space,
 * and normalize the string according to NFKC normalization form.
 */
fun normaliseLegalName(legalName: String): String {
    val trimmedLegalName = legalName.trim().replace(WHITESPACE, " ")
    return Normalizer.normalize(trimmedLegalName, Normalizer.Form.NFKC)
}

private val legalNameRules: List<Rule<String>> = listOf(
        UnicodeNormalizationRule(),
        CharacterRule(',', '=', '$', '"', '\'', '\\'),
        WordRule("node", "server"),
        LengthRule(maxLength = 255),
        // TODO: Implement confusable character detection if we add more scripts.
        UnicodeRangeRule(LATIN, COMMON, INHERITED),
        CapitalLetterRule(),
        X500NameRule(),
        MustHaveAtLeastTwoLettersRule()
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
                "Forbidden characters $illegalChars in \"$legalName\"."
            } else {
                "Forbidden character $illegalChars in \"$legalName\"."
            }
        }
    }
}

private class CharacterRule(vararg val bannedChars: Char) : Rule<String> {
    override fun validate(legalName: String) {
        bannedChars.forEach {
            require(!legalName.contains(it, true)) { "Character not allowed in legal names: $it" }
        }
    }
}

private class WordRule(vararg val bannedWords: String) : Rule<String> {
    override fun validate(legalName: String) {
        bannedWords.forEach {
            require(!legalName.contains(it, ignoreCase = true)) { "Word not allowed in legal names: $it" }
        }
    }
}

private class LengthRule(val maxLength: Int) : Rule<String> {
    override fun validate(legalName: String) {
        require(legalName.length <= maxLength) { "Legal name longer then $maxLength characters." }
    }
}

private class CapitalLetterRule : Rule<String> {
    override fun validate(legalName: String) {
        val capitalizedLegalName = legalName.capitalize()
        require(legalName == capitalizedLegalName) { "Legal name should be capitalized. i.e. '$capitalizedLegalName'" }
    }
}

private class X500NameRule : Rule<String> {
    override fun validate(legalName: String) {
        // This will throw IllegalArgumentException if the name does not comply with X500 name format.
        X500Principal("CN=$legalName")
    }
}

private class MustHaveAtLeastTwoLettersRule : Rule<String> {
    override fun validate(legalName: String) {
        // Try to exclude names like "/", "£", "X" etc.
        require(legalName.count { it.isLetter() } >= 3) { "Illegal input legal name '$legalName'. Legal name must have at least two letters" }
    }
}

private interface Rule<in T> {
    fun validate(legalName: T)
}
