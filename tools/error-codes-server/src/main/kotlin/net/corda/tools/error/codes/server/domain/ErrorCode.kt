package net.corda.tools.error.codes.server.domain

import net.corda.tools.error.codes.server.commons.domain.validation.ValidationResult

data class ErrorCode(val value: String) {

    init {
        val errors = errorsForArgs(value)
        require(errors.isEmpty()) { errors.joinToString() }
    }

    companion object {

        private const val EMPTY_VALUE_ERROR = "Error code cannot be empty."
        private const val VALUE_WITH_WHITESPACE_ERROR = "Error code cannot be empty."

        @JvmStatic
        fun errorsForArgs(value: String): Set<String> {

            if (value.isEmpty()) {
                return setOf(EMPTY_VALUE_ERROR)
            }
            if (value.any(Char::isWhitespace)) {
                return setOf(VALUE_WITH_WHITESPACE_ERROR)
            }
            return emptySet()
        }
    }

    object Valid {

        @JvmStatic
        fun create(value: String): ValidationResult<ErrorCode> {

            val errors = errorsForArgs(value)
            return if (errors.isEmpty()) {
                ValidationResult.valid(ErrorCode(value))
            } else {
                ValidationResult.invalid(errors)
            }
        }
    }
}