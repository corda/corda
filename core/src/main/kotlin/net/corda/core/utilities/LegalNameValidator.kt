@file:JvmName("LegalNameValidator")

package net.corda.core.utilities

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
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
    Rule.legalNameRules.forEach { it.validate(normalizedLegalName) }
}

/**
 * The normalize function will trim the input string, replace any multiple spaces with a single space,
 * and normalize the string according to NFKC normalization form.
 */
fun normaliseLegalName(legalName: String): String {
    val trimmedLegalName = legalName.trim().replace(WHITESPACE, " ")
    return Normalizer.normalize(trimmedLegalName, Normalizer.Form.NFKC)
}

val WHITESPACE = "\\s++".toRegex()

private val mandatoryAttributes = setOf(BCStyle.O, BCStyle.C, BCStyle.L)
private val supportedAttributes = mandatoryAttributes + setOf(BCStyle.CN, BCStyle.ST, BCStyle.OU)

/**
 * Validate X500Name according to Corda X500Name specification
 *
 * Supported attributes:
 * - organisation (O) – VARCHAR(127)
 * - state (ST) – VARCHAR(64) nullable
 * - locality (L) – VARCHAR(64)
 * - country (C) – VARCHAR(2)  - ISO code of the country in which it is registered
 * - organizational-unit (OU) – VARCHAR(64) nullable
 * - common name (CN) – VARCHAR(64)
 *
 * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
 * @see <a href="https://r3-cev.atlassian.net/wiki/spaces/AWG/pages/129206341/Distinguished+name+structure">Design Doc</a>.
 */
fun validateX500Name(x500Name: X500Name) {
    val rDNs = x500Name.rdNs.flatMap { it.typesAndValues.toList() }
    val attributes = rDNs.map { it.type }

    // Duplicate attribute value checks.
    require(attributes.size == attributes.toSet().size) { "X500Name contain duplicate attribute." }

    // Mandatory attribute checks.
    require(attributes.containsAll(mandatoryAttributes)) {
        val missingAttributes = mandatoryAttributes.subtract(attributes).map { BCStyle.INSTANCE.oidToDisplayName(it) }
        "The following attribute${if (missingAttributes.size > 1) "s are" else " is"} missing from the legal name : $missingAttributes"
    }

    // Supported attribute checks.
    require(attributes.subtract(supportedAttributes).isEmpty()) {
        val unsupportedAttributes = attributes.subtract(supportedAttributes).map { BCStyle.INSTANCE.oidToDisplayName(it) }
        "The following attribute${if (unsupportedAttributes.size > 1) "s are" else " is"} not supported in Corda :$unsupportedAttributes"
    }
    // Legal name checks.
    validateLegalName(x500Name.organisation)

    // Attribute data width checks.
    require(x500Name.country.length == 2) { "Invalid country '${x500Name.country}' Country code must be 2 letters ISO code " }
    require(x500Name.country.toUpperCase() == x500Name.country) { "Country code should be in upper case." }
    require(countryCodes.contains(x500Name.country)) { "Invalid country code '${x500Name.country}'" }

    require(x500Name.organisation.length < 127) { "Organisation attribute (O) must contain less then 127 characters." }
    require(x500Name.locality.length < 64) { "Locality attribute (L) must contain less then 64 characters." }

    x500Name.state?.let { require(it.length < 64) { "State attribute (ST) must contain less then 64 characters." } }
    x500Name.organisationUnit?.let { require(x500Name.organisationUnit!!.length < 64) { "Organisation Unit attribute (OU) must contain less then 64 characters." } }
    x500Name.commonName?.let { require(x500Name.commonName!!.length < 64) { "Common Name attribute (CN) must contain less then 64 characters." } }
}

private sealed class Rule<in T> {
    companion object {
        val legalNameRules: List<Rule<String>> = listOf(
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
    }

    abstract fun validate(legalName: T)

    private class UnicodeNormalizationRule : Rule<String>() {
        override fun validate(legalName: String) {
            require(legalName == normaliseLegalName(legalName)) { "Legal name must be normalized. Please use 'normaliseLegalName' to normalize the legal name before validation." }
        }
    }

    private class UnicodeRangeRule(vararg supportScripts: Character.UnicodeScript) : Rule<String>() {
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
