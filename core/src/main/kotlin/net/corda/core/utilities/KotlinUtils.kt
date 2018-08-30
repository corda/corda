@file:KeepForDJVM
package net.corda.core.utilities

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.internal.concurrent.get
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.reflect.KProperty

//
// READ ME FIRST:
// This is a collection of public utilities useful only for Kotlin code. Think carefully before adding anything here and
// make sure it's tested and documented. If you're looking to add a public utility that is also relevant to Java then
// don't put it here but in a separate file called Utils.kt
//

/** Like the + operator but throws [ArithmeticException] in case of integer overflow. */
infix fun Int.exactAdd(b: Int): Int = Math.addExact(this, b)

/** Like the + operator but throws [ArithmeticException] in case of integer overflow. */
infix fun Long.exactAdd(b: Long): Long = Math.addExact(this, b)

/**
 * Usually you won't need this method:
 * * If you're in a companion object, use [contextLogger]
 * * If you're in an object singleton, use [LoggerFactory.getLogger] directly on javaClass
 *
 * Otherwise, this gets the [Logger] for a class using the syntax
 *
 * `private val log = loggerFor<MyClass>()`
 */
inline fun <reified T : Any> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

/** When called from a companion object, returns the logger for the enclosing class. */
fun Any.contextLogger(): Logger = LoggerFactory.getLogger(javaClass.enclosingClass)

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
val Int.days: Duration get() = Duration.ofDays(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer hours: `val twoHours = 2.hours`.
 * @see Duration.ofHours
 */
val Int.hours: Duration get() = Duration.ofHours(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer minutes: `val twoMinutes = 2.minutes`.
 * @see Duration.ofMinutes
 */
val Int.minutes: Duration get() = Duration.ofMinutes(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer seconds: `val twoSeconds = 2.seconds`.
 * @see Duration.ofSeconds
 */
val Int.seconds: Duration get() = Duration.ofSeconds(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer milliseconds: `val twoMillis = 2.millis`.
 * @see Duration.ofMillis
 */
val Int.millis: Duration get() = Duration.ofMillis(toLong())

/**
 * A simple wrapper that enables the use of Kotlin's `val x by transient { ... }` syntax. Such a property
 * will not be serialized, and if it's missing (or the first time it's accessed), the initializer will be
 * used to set it up.
 */
fun <T> transient(initializer: () -> T): PropertyDelegate<T> = TransientProperty(initializer)

/**
 * Simple interface encapsulating the implicit Kotlin contract for immutable property delegates.
 */
interface PropertyDelegate<out T> {
    /**
     * Invoked as part of Kotlin delegated properties construct.
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}

/**
 * Simple interface encapsulating the implicit Kotlin contract for mutable property delegates.
 */
interface VariablePropertyDelegate<T> : PropertyDelegate<T> {
    /**
     * Invoked as part of Kotlin delegated properties construct.
     */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

@CordaSerializable
private class TransientProperty<out T> internal constructor(private val initialiser: () -> T) : PropertyDelegate<T> {
    @Transient
    private var initialised = false
    @Transient
    private var value: T? = null

    @Synchronized
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialised) {
            value = initialiser()
            initialised = true
        }
        return uncheckedCast(value)
    }
}

/** @see NonEmptySet.copyOf */
fun <T> Collection<T>.toNonEmptySet(): NonEmptySet<T> = NonEmptySet.copyOf(this)

/** Same as [Future.get] except that the [ExecutionException] is unwrapped. */
@DeleteForDJVM
fun <V> Future<V>.getOrThrow(timeout: Duration? = null): V = try {
    get(timeout)
} catch (e: ExecutionException) {
    throw e.cause!!
}

private val warnings = mutableSetOf<String>()

/**
 * Utility to help log a warning message only once.
 * It's not thread safe, as in the worst case the warning will be logged twice.
 */
fun Logger.warnOnce(warning: String) {
    if (warning !in warnings) {
        warnings.add(warning)
        this.warn(warning)
    }
}