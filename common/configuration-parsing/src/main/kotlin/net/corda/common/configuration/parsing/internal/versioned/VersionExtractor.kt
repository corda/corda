package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.Valid
import net.corda.common.configuration.parsing.internal.valid

internal class VersionExtractor(versionKey: String, versionDefaultValue: Int?) : Configuration.Version.Extractor {

    private val spec = Spec(versionKey, versionDefaultValue)

    override fun parse(configuration: Config, options: Configuration.Validation.Options): Valid<Int?> {

        return spec.parse(configuration)
    }

    private class Spec(versionKey: String, versionDefaultValue: Int?) : Configuration.Specification<Int?>("Version") {

        private val version by int(key = versionKey).optional(versionDefaultValue)

        override fun parseValid(configuration: Config) = valid(version.valueIn(configuration))
    }
}