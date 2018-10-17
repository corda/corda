package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid

class VersionedConfigurationParser<TYPE> private constructor(private val versionParser: Configuration.Value.Parser<Int?>, private val parsersForVersion: Map<Int, Configuration.Value.Parser<TYPE>>, private val defaultVersion: Int?) : Configuration.Value.Parser<TYPE> {

    companion object {

        fun <T> mapping(versionParser: Configuration.Value.Parser<Int?>, defaultVersion: Int? = null, vararg parseFunctions: Pair<Int, Configuration.Value.Parser<T>>): Configuration.Value.Parser<T> = VersionedConfigurationParser(versionParser, mapOf(*parseFunctions), defaultVersion)
    }

    // TODO sollecitom see if you can get rid of all these `Configuration.Validation.Options(strict = false)` by introducing a separate type.
    private val extractVersion = { config: Config -> versionParser.parse(config, Configuration.Validation.Options(strict = false)) }

    override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<TYPE, Configuration.Validation.Error> {

        val versionRead = extractVersion.invoke(configuration)
        if (versionRead.isInvalid) {
            return invalid(versionRead.errors)
        }
        val version = versionRead.valueIfValid ?: defaultVersion ?: throw Exception.MissingVersionHeader()
        val parseConfiguration = parsersForVersion[version] ?: throw Exception.UnsupportedVersion(version)
        return parseConfiguration.parse(configuration, options)
    }

    sealed class Exception(message: String) : kotlin.Exception(message) {

        class MissingVersionHeader : Exception("No version header found and no default version specified.")

        class UnsupportedVersion(val version: Int) : Exception("Unsupported configuration version $version.")
    }
}