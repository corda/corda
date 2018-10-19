package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.Valid

// TODO sollecitom replace this with 2 types: one able to provide a Configuration.Specification for a Config, and another one, used by the first, which provides a Configuration.Specification given a versionNumber: Int?
class VersionedConfigurationParser<TYPE> private constructor(private val versionParser: Configuration.Value.Parser<Int?>, private val parsersForVersion: Map<Int, Configuration.Value.Parser<TYPE>>, private val defaultVersion: Int?) : Configuration.Value.Parser<TYPE> {

    companion object {

        fun <T> mapping(versionParser: Configuration.Value.Parser<Int?>, defaultVersion: Int? = null, vararg parseFunctions: Pair<Int, Configuration.Value.Parser<T>>): Configuration.Value.Parser<T> = VersionedConfigurationParser(versionParser, mapOf(*parseFunctions), defaultVersion)
    }

    private val extractVersion = { config: Config -> versionParser.parse(config, Configuration.Validation.Options(strict = false)) }

    override fun parse(configuration: Config, options: Configuration.Validation.Options): Valid<TYPE> {

        return extractVersion.invoke(configuration).flatMap { versionRead ->

            val version = versionRead ?: defaultVersion ?: throw Exception.MissingVersionHeader()
            val parseConfiguration = parsersForVersion[version] ?: throw Exception.UnsupportedVersion(version)
            parseConfiguration.parse(configuration, options)
        }
    }

    sealed class Exception(message: String) : kotlin.Exception(message) {

        class MissingVersionHeader : Exception("No version header found and no default version specified.")

        class UnsupportedVersion(val version: Int) : Exception("Unsupported configuration version $version.")
    }
}