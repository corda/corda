package net.corda.common.validation.internal

import java.util.Collections.emptySet

interface Validated<TARGET, ERROR> {

    val value: TARGET

    val errors: Set<ERROR>

    val isValid: Boolean get() = errors.isEmpty()

    val isInvalid: Boolean get() = !isValid

    fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception = { errors -> IllegalArgumentException(errors.joinToString(System.lineSeparator())) }): TARGET

    fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR>

    fun <MAPPED> flatMap(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR>

    fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR>

    companion object {

        fun <T, E> valid(target: T): Validated.Result<T, E> = Validated.Result.Successful(target)

        fun <T, E> invalid(errors: Set<E>): Validated.Result<T, E> = Validated.Result.Unsuccessful(errors)

        fun <T, E> invalid(vararg errors: E): Validated.Result<T, E> = invalid(errors.toSet())

        fun <T, E> withResult(target: T, errors: Set<E>): Validated<T, E> = if (errors.isEmpty()) valid(target) else invalid(errors)
    }

    sealed class Result<TARGET, ERROR> : Validated<TARGET, ERROR> {

        class Successful<TARGET, ERROR>(override val value: TARGET) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {

            override val errors: Set<ERROR> = emptySet<ERROR>()

            override fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception) = value

            override fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {

                return valid(convert.invoke(value))
            }

            override fun <MAPPED> flatMap(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {

                return convert.invoke(value)
            }

            override fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR> {

                return valid(value)
            }
        }

        class Unsuccessful<TARGET, ERROR>(override val errors: Set<ERROR>) : Result<TARGET, ERROR>(), Validated<TARGET, ERROR> {

            init {
                require(errors.isNotEmpty())
            }

            // TODO sollecitom improve
            override val value: TARGET get() = throw IllegalStateException("Invalid state.")

            override fun valueOrThrow(exceptionOnErrors: (Set<ERROR>) -> Exception) = throw exceptionOnErrors.invoke(errors)

            override fun <MAPPED> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {

                return invalid(errors)
            }

            override fun <MAPPED> flatMap(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {

                return invalid(errors)
            }

            override fun <MAPPED_ERROR> mapErrors(convertError: (ERROR) -> MAPPED_ERROR): Validated<TARGET, MAPPED_ERROR> {

                return invalid(errors.asSequence().map(convertError).toSet())
            }
        }
    }
}