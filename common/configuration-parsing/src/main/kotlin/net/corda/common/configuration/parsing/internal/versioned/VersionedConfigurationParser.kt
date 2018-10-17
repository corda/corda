package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated

class VersionedConfigurationParser<TYPE> private constructor(private val extractVersion: (Config) -> Int?, private val parsersForVersion: Map<Int, Configuration.Value.Parser<TYPE>>, private val defaultVersion: Int? = DEFAULT_VERSION_VALUE) : Configuration.Value.Parser<TYPE> {

    companion object {

        const val DEFAULT_VERSION_VALUE = 1

        fun <T> mapping(extractVersion: (Config) -> Int?, vararg parseFunctions: Pair<Int, Configuration.Value.Parser<T>>, defaultVersion: Int? = DEFAULT_VERSION_VALUE): Configuration.Value.Parser<T> = VersionedConfigurationParser(extractVersion, mapOf(*parseFunctions), defaultVersion)
    }

    override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<TYPE, Configuration.Validation.Error> {

        val version = extractVersion.invoke(configuration) ?: defaultVersion ?: throw Exception.MissingVersionHeader()
        val parseConfiguration = parsersForVersion[version] ?: throw Exception.UnsupportedVersion(version)
        return parseConfiguration.parse(configuration, options)
    }

    sealed class Exception(message: String) : kotlin.Exception(message) {

        class MissingVersionHeader : Exception("No version header found and no default version specified.")

        class UnsupportedVersion(val version: Int) : Exception("Unsupported configuration version $version.")
    }
}