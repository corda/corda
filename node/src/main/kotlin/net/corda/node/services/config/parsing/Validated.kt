package net.corda.node.services.config.parsing

// TODO sollecitom move to commons
// TODO sollecitom test
// TODO sollecitom add flatMap and map
interface Validated<TARGET : Any, ERROR> {

    val valueIfValid: TARGET?

    val errors: Set<ERROR>

    val isValid: Boolean get() = errors.isEmpty()

    val isInvalid: Boolean get() = !isValid

    fun orElseThrow(exceptionOnErrors: (Set<ERROR>) -> Exception = { errors -> IllegalArgumentException(errors.joinToString(System.lineSeparator())) }): TARGET = valueIfValid ?: throw exceptionOnErrors.invoke(errors)

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