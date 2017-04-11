package net.corda.core.utilities

import org.apache.commons.lang3.text.WordUtils
import java.lang.Character.UnicodeScript.*
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * This validator is used to enforce rules on Corda's legal names , its intended to be used by the Doorman server and Corda node during the node registration process.
 *
 * Here are the list of the rules:
 * - No trailing or prefixed whitespace.
 * - No double spaces
 * - Blacklist words like "node", "server"
 * - Unicode canonicalisation
 * - Restrict names to Latin scripts to avoid both right-to-left issues, debugging issues when we can't pronounce names over the phone, and character confusability attacks.
 * - Should start with a capital letter
 * - No commas or equals signs
 * - No dollars or quote marks. *We might need to relax the quote mark constraint in future to handle Irish company names.
 */
object LegalNameValidator {
    private val rules: List<Rule<String>> = listOf(
            WhiteSpaceRule(),
            CharacterRule(',', '=', '$', '"', '\'', '\\'),
            WordRule("node", "server"),
            LengthRule(maxLength = 255),
            UnicodeRangeRule(LATIN, COMMON, INHERITED),
            CapitalLetterRule()
    )

    fun validate(legalName: String): String {
        val normalizedLegalName = Normalizer.normalize(legalName, Normalizer.Form.NFKC)
        rules.forEach { it.validate(normalizedLegalName) }
        return normalizedLegalName
    }
}

class WhiteSpaceRule : Rule<String> {
    override fun validate(t: String) {
        require(!t.contains("  ")) { "No double spaces" }
        require(!t.endsWith(" ")) { "No trailing whitespace" }
        require(!t.startsWith(" ")) { "No prefixed whitespace" }
    }
}

class UnicodeRangeRule(vararg supportScripts: Character.UnicodeScript) : Rule<String> {
    // TODO: Implement confusable detection if we add more scripts.
    private val pattern = supportScripts.map { "\\p{Is$it}" }.joinToString(separator = "", prefix = "[", postfix = "]*").let { Pattern.compile(it) }

    override fun validate(t: String) {
        require(pattern.matcher(t).matches()) {
            val illegalChars = t.replace(pattern.toRegex(), "").toSet()
            if (illegalChars.size > 1) {
                "Illegal characters $illegalChars in \"$t\"."
            } else {
                "Illegal character $illegalChars in \"$t\"."
            }
        }
    }
}

class CharacterRule(vararg val bannedChars: Char) : Rule<String> {
    override fun validate(t: String) {
        bannedChars.forEach {
            require(!t.contains(it, true)) { "Illegal character: $it" }
        }
    }
}

class WordRule(vararg val bannedWords: String) : Rule<String> {
    override fun validate(t: String) {
        bannedWords.forEach {
            require(!t.contains(it, ignoreCase = true)) { "Illegal word: $it" }
        }
    }
}

class LengthRule(val maxLength: Int) : Rule<String> {
    override fun validate(t: String) {
        require(t.length <= maxLength) { "String longer then 255 characters." }
    }
}

class CapitalLetterRule : Rule<String> {
    override fun validate(t: String) {
        require(t == WordUtils.capitalize(t)) { "Legal name should be capitalized." }
    }
}

interface Rule<in T> {
    fun validate(t: T)
}
