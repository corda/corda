package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.configObject
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class VersionedConfigurationParserTest {

    @Test
    fun correct_matching_function_is_used_for_supported_version() {


    }

    @Test
    fun unsupported_version_raises_exception() {


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

    private val extractMissingVersion: Configuration.Value.Parser<Int?> = extractValidValue(null)

    // TODO sollecitom move to common
    private fun <VALUE> extractValidValue(value: VALUE) = extractValue(valid(value))

    // TODO sollecitom move to common
    private fun <VALUE> extractValueWithErrors(errors: Set<Configuration.Validation.Error>) = extractValue<VALUE>(invalid(errors))

    // TODO sollecitom move to common
    private fun <VALUE> extractValue(value: Validated<VALUE, Configuration.Validation.Error>) = object : Configuration.Value.Parser<VALUE> {

        override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<VALUE, Configuration.Validation.Error> = value
    }
}