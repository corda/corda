package net.corda.tools.error.codes.server.domain

import net.corda.tools.error.codes.server.commons.domain.validation.ValidationResult

data class ReleaseVersion(val major: Int, val minor: Int = 0, val patch: Int = 0) {

    init {
        val errors = errorsForArgs(major, minor, patch)
        require(errors.isEmpty()) { errors.joinToString() }
    }

    fun description(): String = "$major$SEPARATOR$minor$SEPARATOR$patch"

    override fun toString() = description()

    companion object {

        const val SEPARATOR = "."

        private const val MAJOR_NEGATIVE_VALUE_ERROR = "Major release version part cannot be negative."
        private const val MINOR_NEGATIVE_VALUE_ERROR = "Minor release version part cannot be negative."
        private const val PATCH_NEGATIVE_VALUE_ERROR = "Patch release version part cannot be negative."

        @JvmStatic
        fun errorsForArgs(major: Int, minor: Int, patch: Int): Set<String> {

            val errors = mutableSetOf<String>()
            if (major < 0) {
                errors += MAJOR_NEGATIVE_VALUE_ERROR
            }
            if (minor < 0) {
                errors += MINOR_NEGATIVE_VALUE_ERROR
            }
            if (patch < 0) {
                errors += PATCH_NEGATIVE_VALUE_ERROR
            }
            return errors
        }
    }

    object Valid {

        @JvmStatic
        fun create(major: Int, minor: Int = 0, patch: Int = 0): ValidationResult<ReleaseVersion> {

            val errors = errorsForArgs(major, minor, patch)
            return if (errors.isEmpty()) {
                ValidationResult.valid(ReleaseVersion(major, minor, patch))
            } else {
                ValidationResult.invalid(errors)
            }
        }
    }
}