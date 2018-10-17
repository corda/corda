package net.corda.node.services.config.parsing.common.validation

// TODO sollecitom move
interface Validator<TARGET : Any, ERROR : Any, OPTIONS> {

    fun validate(target: TARGET, options: OPTIONS? = null): Validated<TARGET, ERROR>
}