package net.corda.node.services.config.parsing

interface Validator<TARGET, ERROR> {

    fun validate(target: TARGET): Set<ERROR>

    fun isValid(target: TARGET): Boolean = validate(target).isEmpty()

    fun rejectIfInvalid(target: TARGET, toException: (Set<ERROR>) -> Exception) {

        val errors = validate(target)
        if (errors.isNotEmpty()) {
            throw toException(errors)
        }
    }
}