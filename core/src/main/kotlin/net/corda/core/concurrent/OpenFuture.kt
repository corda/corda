package net.corda.core.concurrent

/** A [CordaFuture] with additional methods to complete it with a value, exception or the outcome of another future. */
interface OpenFuture<V> : ValueOrException<V>, CordaFuture<V>