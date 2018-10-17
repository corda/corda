package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.configObject
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.valid
import org.assertj.core.api.Assertions.assertThat
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

    private fun <VALUE> extractValidValue(value: VALUE) = object : Configuration.Value.Parser<VALUE> {

        override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<VALUE, Configuration.Validation.Error> {

            return valid(value)
        }
    }
}