package net.corda.node.services.config.parsing

import net.corda.node.services.config.parsing.common.validation.Validated

inline fun <TYPE, reified MAPPED : Any> Configuration.Property.Definition.Standard<TYPE>.map(noinline convert: (String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): Configuration.Property.Definition.Standard<MAPPED> = this.map(MAPPED::class.java.simpleName, convert)

inline fun <reified ENUM : Enum<ENUM>, VALUE : Any> Configuration.Specification<VALUE>.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.map(noinline convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)