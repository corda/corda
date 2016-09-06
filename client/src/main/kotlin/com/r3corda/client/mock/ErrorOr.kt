package com.r3corda.client.mock

class ErrorOr<out A> private constructor(
        val value: A?,
        val error: Exception?
) {
    constructor(value: A): this(value, null)
    constructor(error: Exception): this(null, error)

    fun <T> match(onValue: (A) -> T, onError: (Exception) -> T): T {
        if (value != null) {
            return onValue(value)
        } else {
            return onError(error!!)
        }
    }

    fun getValueOrThrow(): A {
        if (value != null) {
            return value
        } else {
            throw error!!
        }
    }

    // Functor
    fun <B> map(function: (A) -> B): ErrorOr<B> {
        return ErrorOr(value?.let(function), error)
    }

    // Applicative
    fun <B, C> combine(other: ErrorOr<B>, function: (A, B) -> C): ErrorOr<C> {
        return ErrorOr(value?.let { a -> other.value?.let { b -> function(a, b) } }, error ?: other.error)
    }

    // Monad
    fun <B> bind(function: (A) -> ErrorOr<B>): ErrorOr<B> {
        return value?.let(function) ?: ErrorOr<B>(error!!)
    }
}
