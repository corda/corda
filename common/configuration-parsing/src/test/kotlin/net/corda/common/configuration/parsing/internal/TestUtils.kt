package net.corda.common.configuration.parsing.internal

import com.typesafe.config.Config
import net.corda.common.validation.internal.Validated

internal val extractMissingVersion: Configuration.Value.Parser<Int?> = extractVersion(null)

internal fun extractVersion(value: Int?) = extractValidValue(value)

internal fun extractPresentVersion(value: Int) = extractValidValue(value)

internal fun <VALUE> extractValidValue(value: VALUE) = extractValue(Validated.valid(value))

internal fun <VALUE> extractValueWithErrors(errors: Set<Configuration.Validation.Error>) = extractValue<VALUE>(Validated.invalid(errors))

internal fun <VALUE> extractValue(value: Valid<VALUE>) = object : Configuration.Value.Parser<VALUE> {

    override fun parse(configuration: Config, options: Configuration.Options): Valid<VALUE> = value
}