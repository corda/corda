package net.corda.node.services.config.parsing

import net.corda.node.services.config.parsing.common.validation.Validated

inline fun <TYPE, reified MAPPED : Any> Configuration.Property.Definition.Standard<TYPE>.map(noinline convert: (String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): Configuration.Property.Definition.Standard<MAPPED> = this.map(MAPPED::class.java.simpleName, convert)