package net.corda.core.utilities

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Get the [Logger] for a class using the syntax
 *
 * `val logger = loggerFor<MyClass>()`
 */
inline fun <reified T : Any> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

/** Log a TRACE level message produced by evaluating the given lamdba, but only if TRACE logging is enabled. */
inline fun Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

/** Log a DEBUG level message produced by evaluating the given lamdba, but only if DEBUG logging is enabled. */
inline fun Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}

/** @see NonEmptySet.copyOf */
fun <T> Collection<T>.toNonEmptySet(): NonEmptySet<T> = NonEmptySet.copyOf(this)