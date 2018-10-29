package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.Valid
import net.corda.common.configuration.parsing.internal.valid
import net.corda.common.validation.internal.Validated.Companion.invalid

class VersionedSpecificationRegistry<VALUE> private constructor(private val versionFromConfig: (Config) -> Valid<Int>, private val specifications: Map<Int, Configuration.Specification<VALUE>>) : (Config) -> Valid<Configuration.Specification<VALUE>> {

    companion object {

        fun <V> mapping(versionParser: Configuration.Value.Parser<Int>, specifications: Map<Int, Configuration.Specification<V>>) = VersionedSpecificationRegistry({ config -> versionParser.parse(config) }, specifications)

        fun <V> mapping(versionParser: Configuration.Value.Parser<Int>, vararg specifications: Pair<Int, Configuration.Specification<V>>) = VersionedSpecificationRegistry({ config -> versionParser.parse(config) }, specifications.toMap())

        fun <V> mapping(versionParser: (Config) -> Valid<Int>, specifications: Map<Int, Configuration.Specification<V>>) = VersionedSpecificationRegistry(versionParser, specifications)

        fun <V> mapping(versionParser: (Config) -> Valid<Int>, vararg specifications: Pair<Int, Configuration.Specification<V>>) = VersionedSpecificationRegistry(versionParser, specifications.toMap())
    }

    override fun invoke(configuration: Config): Valid<Configuration.Specification<VALUE>> {

        return versionFromConfig.invoke(configuration).mapValid { version ->

            val value = specifications[version]
            value?.let { valid(it) } ?: invalid<Configuration.Specification<VALUE>, Configuration.Validation.Error>(Configuration.Validation.Error.UnsupportedVersion.of(version))
        }
    }
}