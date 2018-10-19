package net.corda.common.configuration.parsing.internal.versioned

import net.corda.common.configuration.parsing.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class VersionedConfigurationParserTest {

    @Test
    fun correct_matching_function_is_used_for_supported_version() {

        val defaultVersion = 1
        val version = 5
        val valueForVersion = "Five"

        val configuration = configObject().toConfig()
        val parser = VersionedConfigurationParser.mapping(extractVersion(version), defaultVersion, version to extractValidValue(valueForVersion), 1 to extractValidValue("One"))

        val result = parser.parse(configuration, Configuration.Validation.Options(strict = false))

        assertThat(result.isValid).isTrue()
        assertThat(result.value).isEqualTo(valueForVersion)
    }

    @Test
    fun unsupported_version_raises_exception() {

        val defaultVersion = 1
        val unsupportedVersion = 5

        val configuration = configObject().toConfig()
        val parser = VersionedConfigurationParser.mapping(extractVersion(unsupportedVersion), defaultVersion, 1 to extractValidValue("One"), 2 to extractValidValue("Two"))

        assertThatThrownBy { parser.parse(configuration, Configuration.Validation.Options(strict = false)) }.isInstanceOf(VersionedConfigurationParser.Exception.UnsupportedVersion::class.java)
    }

    @Test
    fun missing_version_raises_exception() {

        val defaultVersion = null

        val configuration = configObject().toConfig()
        val parser = VersionedConfigurationParser.mapping<String>(extractMissingVersion, defaultVersion)

        assertThatThrownBy { parser.parse(configuration, Configuration.Validation.Options(strict = false)) }.isInstanceOf(VersionedConfigurationParser.Exception.MissingVersionHeader::class.java)
    }

    @Test
    fun invalid_version_errors_are_propagated() {

        val configuration = configObject().toConfig()

        val error = Configuration.Validation.Error.WrongType.of("blah.version", "Version property must be of type <Int>, not <Boolean>", "Int")
        val extractVersion = extractValueWithErrors<Int?>(setOf(error))
        val parser = VersionedConfigurationParser.mapping<String>(extractVersion)

        val errors = parser.parse(configuration, Configuration.Validation.Options(strict = false)).errors

        assertThat(errors).hasSize(1)
        assertThat(errors.first()).isEqualTo(error)
    }

    @Test
    fun missing_version_defaults_to_default_value() {

        val defaultVersion = 3
        val expectedResult = "booya!"

        val configuration = configObject().toConfig()
        val parser = VersionedConfigurationParser.mapping(extractMissingVersion, defaultVersion, defaultVersion to extractValidValue(expectedResult))

        val result = parser.parse(configuration, Configuration.Validation.Options(strict = false)).valueOrThrow()

        assertThat(result).isEqualTo(expectedResult)
    }
}