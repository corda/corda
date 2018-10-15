package net.corda.node.services.config.parsing

import com.typesafe.config.Config

// TODO sollecitom use `Valid` here
typealias ParseConfig<TYPED> = (Config) -> TYPED

typealias ExtractConfigVersion = (Config) -> Int?

// TODO sollecitom use `Valid` here
class VersionedConfigParser<TYPED>(private val extractVersion: ExtractConfigVersion, private val versionToParseFunction: Map<Int, ParseConfig<out TYPED>>, private val defaultVersion: Int? = 0) : ParseConfig<TYPED> {

    companion object {

        fun <T> mapping(extractVersion: ExtractConfigVersion, vararg parseFunctions: Pair<Int, ParseConfig<out T>>, defaultVersion: Int? = 0) = VersionedConfigParser(extractVersion, mapOf(*parseFunctions), defaultVersion)
    }

    override fun invoke(configuration: Config): TYPED {

        val version = extractVersion.invoke(configuration) ?: defaultVersion ?: throw VersionedConfigParser.Exception.MissingVersionHeader()
        val parseConfiguration = versionToParseFunction[version] ?: throw VersionedConfigParser.Exception.UnsupportedVersion(version)
        return parseConfiguration.invoke(configuration)
    }

    sealed class Exception(message: String) : kotlin.Exception(message) {

        class MissingVersionHeader : VersionedConfigParser.Exception("No version header found and no default version specified.")

        class UnsupportedVersion(val version: Int) : VersionedConfigParser.Exception("Unsupported configuration version $version.")
    }
}