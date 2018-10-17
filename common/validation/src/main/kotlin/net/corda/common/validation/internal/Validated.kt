package net.corda.common.validation.internal

import java.util.Collections.emptySet

interface Validated<TARGET, ERROR> {

    val valueIfValid: TARGET?

    val errors: Set<ERROR>

    val isValid: Boolean get() = errors.isEmpty()

    val isInvalid: Boolean get() = !isValid

    fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception = { errors -> IllegalArgumentException(errors.joinToString(System.lineSeparator())) }): TARGET

    fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {

        return valueIfValid?.let(convert)?.let { valid<MAPPED, ERROR>(it) } ?: invalid(errors)
    }

    fun <MAPPED> flatMap(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {

        return valueIfValid?.let(convert) ?: invalid(errors)
    }

    fun <MAPPED : Any, MAPPED_ERROR> flatMapWithErrors(convert: (TARGET) -> Validated<MAPPED, MAPPED_ERROR>, convertError: (ERROR) -> MAPPED_ERROR): Validated<MAPPED, MAPPED_ERROR> {

        return valueIfValid?.let(convert) ?: invalid(errors.asSequence().map(convertError).toSet())
    }

    companion object {

        fun <T, E> valid(target: T): Validated.Result<T, E> = Validated.Result.Successful(target)

        fun <T, E> invalid(errors: Set<E>): Validated.Result<T, E> = Validated.Result.Unsuccessful(errors)

        fun <T, E> invalid(vararg errors: E): Validated.Result<T, E> = invalid(errors.toSet())

        fun <T, E> withResult(target: T, errors: Set<E>): Validated<T, E> = if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    sealed class Result<TARGET, ERROR> : Validated<TARGET, ERROR> {

        class Successful<TARGET, ERROR>(override val valueIfValid: TARGET) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {

            override val errors: Set<ERROR> = emptySet<ERROR>()

            override fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception) = valueIfValid
        }

        class Unsuccessful<TARGET, ERROR>(override val errors: Set<ERROR>) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {

            init {
                require(errors.isNotEmpty())
            }

            override val valueIfValid: TARGET? = null

            override fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception) = throw exceptionOnErrors.invoke(errors)
        }
    }
}