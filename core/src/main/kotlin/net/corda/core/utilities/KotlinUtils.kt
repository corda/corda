package net.corda.core.utilities

import net.corda.core.internal.concurrent.get
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.reflect.KProperty

//
// READ ME FIRST:
// This is a collection of public utilities useful only for Kotlin code. If you're looking to add a public utility that
// is also relevant to Java then put it in Utils.kt.
//

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

/**
 * Extension method for easier construction of [Duration]s in terms of integer days: `val twoDays = 2.days`.
 * @see Duration.ofDays
 */
inline val Int.days: Duration get() = Duration.ofDays(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer hours: `val twoHours = 2.hours`.
 * @see Duration.ofHours
 */
inline val Int.hours: Duration get() = Duration.ofHours(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer minutes: `val twoMinutes = 2.minutes`.
 * @see Duration.ofMinutes
 */
inline val Int.minutes: Duration get() = Duration.ofMinutes(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer seconds: `val twoSeconds = 2.seconds`.
 * @see Duration.ofSeconds
 */
inline val Int.seconds: Duration get() = Duration.ofSeconds(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer milliseconds: `val twoMillis = 2.millis`.
 * @see Duration.ofMillis
 */
inline val Int.millis: Duration get() = Duration.ofMillis(toLong())

/**
 * A simple wrapper that enables the use of Kotlin's `val x by transient { ... }` syntax. Such a property
 * will not be serialized, and if it's missing (or the first time it's accessed), the initializer will be
 * used to set it up.
 */
@Suppress("DEPRECATION")
fun <T> transient(initializer: () -> T) = TransientProperty(initializer)

@Deprecated("Use transient")
@CordaSerializable
class TransientProperty<out T>(private val initialiser: () -> T) {
    @Transient private var initialised = false
    @Transient private var value: T? = null

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialised) {
            value = initialiser()
            initialised = true
        }
        return value as T
    }
}

/** @see NonEmptySet.copyOf */
fun <T> Collection<T>.toNonEmptySet(): NonEmptySet<T> = NonEmptySet.copyOf(this)

/** Same as [Future.get] except that the [ExecutionException] is unwrapped. */
fun <V> Future<V>.getOrThrow(timeout: Duration? = null): V = try {
    get(timeout)
} catch (e: ExecutionException) {
    throw e.cause!!
}
