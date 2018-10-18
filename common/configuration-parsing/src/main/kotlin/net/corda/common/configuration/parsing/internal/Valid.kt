package net.corda.common.configuration.parsing.internal

import net.corda.common.validation.internal.Validated

internal typealias Valid<TARGET> = Validated<TARGET, Configuration.Validation.Error>

internal fun <TYPE> valid(target: TYPE) = Validated.valid<TYPE, Configuration.Validation.Error>(target)