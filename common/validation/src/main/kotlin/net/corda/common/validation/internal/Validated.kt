package net.corda.common.validation.internal

import java.util.Collections.emptySet

/**
 * A monad, providing information about validation result.
 * It wraps either a valid [TARGET] or a set of [ERROR].
 */
interface Validated<TARGET, ERROR> {
    /**
     * Returns a valid [TARGET] if no validation errors are present. Otherwise, it throws the exception produced by [exceptionOnErrors], defaulting to [IllegalStateException].
     *
     * @throws IllegalStateException or the result of [exceptionOnErrors] if there are errors.
     */
    fun value(exceptionOnErrors: (Set<ERROR>) -> Exception = { errors -> IllegalStateException(errors.joinToString(System.lineSeparator())) }): TARGET

    /**
     * The errors produced during validation, if any.
     */
    val errors: Set<ERROR>

    /**
     * Whether a valid [TARGET] value is present.
     */
    val isValid: Boolean get() = errors.isEmpty()

    /**
     * Whether there were validation errors.
     */
    val isInvalid: Boolean get() = !isValid

    /**
     * Returns the underlying value as optional, with a null result instead of an exception if validation rules were violated.
     */
    val optional: TARGET? get() = if (isValid) value() else null

    /**
     * Applies the [convert] function to the [TARGET] value, if valid. Otherwise, returns a [Validated] monad with a [MAPPED] generic type and the current errors set.
     */
    fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR>

    /**
     * Applies the [convert] function to the [TARGET] value, if valid, returning its [Validated] output. Otherwise, returns a [Validated] monad with a [MAPPED] generic type and the current errors set.
     */
    fun <MAPPED> mapValid(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR>

    /**
     * Applies the [convertError] function to the errors set, if not empty. Otherwise, returns a [Validated] wrapper with a [MAPPED_ERROR] generic type.
     */
    fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR>

    /**
     * Performs the given [action] if the underlying value is valid.
     * @return itself for fluent chained invocation.
     */
    fun doIfValid(action: (TARGET) -> Unit): Validated<TARGET, ERROR> {
        if (isValid) {
            action.invoke(value())
        }
        return this
    }

    /**
     * Performs the given [action] if the underlying value is invalid.
     * @return itself for fluent chained invocation.
     */
    fun doOnErrors(action: (Set<ERROR>) -> Unit): Validated<TARGET, ERROR> {
        if (isInvalid) {
            action.invoke(errors)
        }
        return this
    }

    companion object {
        /**
         * Constructs a [Validated] wrapper with given valid [target] value and no errors.
         */
        fun <T, E> valid(target: T): Validated.Result<T, E> = Validated.Result.Successful(target)

        /**
         * Constructs an invalid [Validated] wrapper with given errors and no value.
         */
        fun <T, E> invalid(errors: Set<E>): Validated.Result<T, E> = Validated.Result.Unsuccessful(errors)

        /**
         * @see invalid
         */
        fun <T, E> invalid(vararg errors: E): Validated.Result<T, E> = invalid(errors.toSet())

        /**
         * Constructs a [Validated] wrapper with valid [T] value if [errors] is empty. Otherwise, it constructs an invalid wrapper with no value.
         *
         * @see valid
         * @see invalid
         */
        fun <T, E> withResult(target: T, errors: Set<E>): Validated<T, E> = if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    /**
     * Models the result of validating a [TARGET] value, producing [ERROR]s if rules are violated.
     */
    sealed class Result<TARGET, ERROR> : Validated<TARGET, ERROR> {
        /**
         * A successful validation result, containing a valid [TARGET] value and no [ERROR]s.
         */
        class Successful<TARGET, ERROR>(private val value: TARGET) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {
            override val errors: Set<ERROR> = emptySet<ERROR>()

            override fun value(exceptionOnErrors: (Set<ERROR>) -> Exception) = value

            override fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {
                return valid(convert.invoke(value))
            }

            override fun <MAPPED> mapValid(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {
                return convert.invoke(value)
            }

            override fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR> {
                return valid(value)
            }
        }

        /**
         * An unsuccessful validation result, containing [ERROR]s and no valid [TARGET] value.
         */
        class Unsuccessful<TARGET, ERROR>(override val errors: Set<ERROR>) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {
            init {
                require(errors.isNotEmpty()) { "No errors encountered during validation" }
            }

            override fun value(exceptionOnErrors: (Set<ERROR>) -> Exception) = throw exceptionOnErrors.invoke(errors)

            override fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {
                return invalid(errors)
            }

            override fun <MAPPED> mapValid(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {
                return invalid(errors)
            }

            override fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR> {
                return invalid(errors.asSequence().map(convertError).toSet())
            }
        }
    }
}