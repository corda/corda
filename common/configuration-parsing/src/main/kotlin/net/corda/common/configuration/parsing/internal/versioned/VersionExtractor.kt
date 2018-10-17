package net.corda.common.configuration.parsing.internal.versioned

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.valid

class VersionExtractor(versionKey: String, val versionDefaultValue: Int = DEFAULT_VERSION_VALUE) : Configuration.Version.Extractor {

    companion object {

        const val DEFAULT_VERSION_VALUE = 1
    }

    private val spec = Spec(versionKey, versionDefaultValue)

    override fun parse(configuration: Config, options: Configuration.Validation.Options): Validated<Int, Configuration.Validation.Error> {

        return spec.parse(configuration, Configuration.Validation.Options(false))
    }

    private class Spec(versionKey: String, versionDefaultValue: Int?) : Configuration.Specification<Int>("Version") {

        private val version by long(key = versionKey).map { _, _, value -> valid<Int, Configuration.Validation.Error>(value.toInt()) }.optional(versionDefaultValue)

        override fun parseValid(configuration: Config) = version.valueIn(configuration)!!
    }
}