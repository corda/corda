package net.corda.node.services.config.parsing

import com.typesafe.config.Config

typealias ParseConfig<TYPED> = (Config) -> TYPED

typealias ExtractConfigVersion = (Config) -> Int?

class VersionedConfigParser<TYPED>(private val extractVersion: ExtractConfigVersion, private val versionToParseFunction: Map<Int, ParseConfig<out TYPED>>, private val defaultVersion: Int? = 0) : ParseConfig<TYPED> {

    constructor(extractVersion: ExtractConfigVersion, vararg parseFunctions: Pair<Int, ParseConfig<out TYPED>>, defaultVersion: Int? = 0) : this(extractVersion, mapOf(*parseFunctions), defaultVersion)

    override fun invoke(configuration: Config): TYPED {

        // TODO sollecitom change this exception type to something sensible.
        val version = extractVersion.invoke(configuration) ?: defaultVersion ?: throw IllegalArgumentException("No version header found and no default version specified.")
        // TODO sollecitom change this exception type to something sensible.
        val parseConfiguration = versionToParseFunction[version] ?: throw IllegalArgumentException("Unsupported configuration version $version.")
        return parseConfiguration.invoke(configuration)
    }
}