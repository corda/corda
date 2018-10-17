package net.corda.common.validation.internal

interface Validator<TARGET : Any, ERROR : Any, OPTIONS> {

    fun validate(target: TARGET, options: OPTIONS? = null): Validated<TARGET, ERROR>
}