package net.corda.common.validation.internal

/**
 * Defines validation behaviour for [TARGET] value and given [OPTIONS], raising [ERROR]s if rules are violated.
 */
interface Validator<TARGET : Any, ERROR : Any, OPTIONS> {

    /**
     * Validates [target] using given [options], producing a [Validated] monad wrapping either a valid [target] or a set of [ERROR]s.
     */
    fun validate(target: TARGET, options: OPTIONS): Validated<TARGET, ERROR>
}