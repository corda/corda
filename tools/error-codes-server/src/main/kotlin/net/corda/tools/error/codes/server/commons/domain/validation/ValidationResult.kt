package net.corda.tools.error.codes.server.commons.domain.validation

interface ValidationResult<TARGET : Any> {

    val value: TARGET?

    val errors: Set<String>

    val isValid: Boolean
        get() = errors.isEmpty()

    val isInvalid: Boolean
        get() = !isValid

    fun validValue(exceptionOnErrors: (Set<String>) -> Exception = { errors -> IllegalArgumentException(errors.joinToString(System.lineSeparator())) }): TARGET = value ?: throw exceptionOnErrors.invoke(errors)

    companion object {

        fun <T : Any> valid(target: T): ValidationResult<T> = Result(target, emptySet())

        fun <T : Any> invalid(errors: Set<String>): ValidationResult<T> = Result(null, errors)
    }

    private class Result<TARGET : Any>(override val value: TARGET?, override val errors: Set<String>) : ValidationResult<TARGET> {

        init {
            require(value != null && errors.isEmpty() || value == null && errors.isNotEmpty())
        }
    }
}