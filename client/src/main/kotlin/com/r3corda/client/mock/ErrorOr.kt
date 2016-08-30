package com.r3corda.client.mock

sealed class ErrorOr<out A> {
    class Error<A>(val error: String): ErrorOr<A>()
    class Success<A>(val value: A): ErrorOr<A>()

    fun getValueOrThrow(): A {
        return when (this) {
            is ErrorOr.Error -> throw Exception(this.error)
            is ErrorOr.Success -> this.value
        }
    }

    // Functor
    fun <B> map(function: (A) -> B): ErrorOr<B> {
        return when (this) {
            is ErrorOr.Error -> ErrorOr.Error(error)
            is ErrorOr.Success -> ErrorOr.Success(function(value))
        }
    }

    // Applicative
    fun <B, C> combine(other: ErrorOr<B>, function: (A, B) -> C): ErrorOr<C> {
        return when (this) {
            is ErrorOr.Error -> ErrorOr.Error(error)
            is ErrorOr.Success -> when (other) {
                is ErrorOr.Error -> ErrorOr.Error(other.error)
                is ErrorOr.Success -> ErrorOr.Success(function(value, other.value))
            }
        }
    }

    // Monad
    fun <B> bind(function: (A) -> ErrorOr<B>): ErrorOr<B> {
        return when (this) {
            is ErrorOr.Error -> ErrorOr.Error(error)
            is ErrorOr.Success -> function(value)
        }
    }
}
