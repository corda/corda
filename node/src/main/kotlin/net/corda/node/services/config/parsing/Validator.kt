package net.corda.node.services.config.parsing

interface Validator<TARGET, ERROR, OPTIONS> {

    fun validate(target: TARGET, options: OPTIONS? = null): Set<ERROR>

    fun isValid(target: TARGET, options: OPTIONS? = null): Boolean = validate(target, options).isEmpty()

    fun rejectIfInvalid(target: TARGET, options: OPTIONS? = null, toException: (Set<ERROR>) -> Exception) {

        val errors = validate(target, options)
        if (errors.isNotEmpty()) {
            throw toException(errors)
        }
    }
}