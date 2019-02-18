package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.Valid
import net.corda.common.configuration.parsing.internal.valid

internal class VersionExtractor(versionPath: String, versionDefaultValue: Int) : Configuration.Version.Extractor {
    private val containingPath = versionPath.split(".").let { if (it.size > 1) it.subList(0, it.size - 1) else null }
    private val key = versionPath.split(".").last()

    private val spec = Spec(key, versionDefaultValue, containingPath?.joinToString("."))

    override fun parse(configuration: Config, options: Configuration.Options): Valid<Int> {
        return spec.parse(configuration)
    }

    private class Spec(key: String, versionDefaultValue: Int, prefix: String?) : Configuration.Specification<Int>("Version", prefix) {
        private val version by int(key = key).optional().withDefaultValue(versionDefaultValue)
        override fun parseValid(configuration: Config, options: Configuration.Options) = valid(version.valueIn(configuration, options))
    }
}