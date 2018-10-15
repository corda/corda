package net.corda.node.services.config.parsing

// TODO sollecitom move to commons
// TODO sollecitom test
interface Validated<TARGET : Any, ERROR> {

    val valueIfValid: TARGET?

    val errors: Set<ERROR>

    val isValid: Boolean get() = errors.isEmpty()

    val isInvalid: Boolean get() = !isValid

    fun orElseThrow(exceptionOnErrors: (Set<ERROR>) -> Exception = { errors -> IllegalArgumentException(errors.joinToString(System.lineSeparator())) }): TARGET = valueIfValid ?: throw exceptionOnErrors.invoke(errors)

    fun <MAPPED : Any> map(convert: (TARGET) -> MAPPED): Validated<MAPPED, ERROR> {

        return valueIfValid?.let(convert)?.let { valid<MAPPED, ERROR>(it) } ?: invalid(errors)
    }

    fun <MAPPED : Any> flatMap(convert: (TARGET) -> Validated<MAPPED, ERROR>): Validated<MAPPED, ERROR> {

        return valueIfValid?.let(convert) ?: invalid(errors)
    }

    fun <MAPPED : Any, MAPPED_ERROR> flatMapErrors(convert: (TARGET) -> Validated<MAPPED, MAPPED_ERROR>, convertError: (ERROR) -> MAPPED_ERROR): Validated<MAPPED, MAPPED_ERROR> {

        return valueIfValid?.let(convert) ?: invalid(errors.asSequence().map(convertError).toSet())
    }

    companion object {

        // TODO sollecitom consider a `valueOf(target: T, errors: Set<E>): Validated<T, E>` function
        fun <T : Any, E> valid(target: T): Validated<T, E> = Result(target, emptySet())

        fun <T : Any, E> invalid(errors: Set<E>): Validated<T, E> = Result(null, errors)

        fun <T : Any, E> invalid(vararg errors: E): Validated<T, E> = invalid(errors.toSet())
    }

    private class Result<TARGET : Any, ERROR>(override val valueIfValid: TARGET?, override val errors: Set<ERROR>) : Validated<TARGET, ERROR> {

        init {
            require(valueIfValid != null && errors.isEmpty() || valueIfValid == null && errors.isNotEmpty())
        }
    }
}