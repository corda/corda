package com.r3corda.core

import com.google.common.io.ByteStreams
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.crypto.newSecureRandom
import org.slf4j.Logger
import java.io.BufferedInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.temporal.Temporal
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock
import kotlin.reflect.KProperty

val Int.days: Duration get() = Duration.ofDays(this.toLong())
val Int.hours: Duration get() = Duration.ofHours(this.toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())

val Int.bd: BigDecimal get() = BigDecimal(this)
val Double.bd: BigDecimal get() = BigDecimal(this)
val String.bd: BigDecimal get() = BigDecimal(this)
val Long.bd: BigDecimal get() = BigDecimal(this)

fun String.abbreviate(maxWidth: Int): String = if (length <= maxWidth) this else take(maxWidth - 1) + "…"

/**
 * Returns a random positive long generated using a secure RNG. This function sacrifies a bit of entropy in order to
 * avoid potential bugs where the value is used in a context where negative numbers are not expected.
 */
fun random63BitValue(): Long = Math.abs(newSecureRandom().nextLong())

// Some utilities for working with Guava listenable futures.
fun <T> ListenableFuture<T>.then(executor: Executor, body: () -> Unit) = addListener(Runnable(body), executor)

fun <T> ListenableFuture<T>.success(executor: Executor, body: (T) -> Unit) = then(executor) {
    val r = try {
        get()
    } catch(e: Throwable) {
        return@then
    }
    body(r)
}

fun <T> ListenableFuture<T>.failure(executor: Executor, body: (Throwable) -> Unit) = then(executor) {
    try {
        get()
    } catch(e: Throwable) {
        body(e)
    }
}

infix fun <T> ListenableFuture<T>.then(body: () -> Unit): ListenableFuture<T> = apply { then(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.success(body: (T) -> Unit): ListenableFuture<T> = apply { success(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.failure(body: (Throwable) -> Unit): ListenableFuture<T> = apply { failure(RunOnCallerThread, body) }

fun <R> Path.use(block: (InputStream) -> R): R = Files.newInputStream(this).use(block)

/** Executes the given block and sets the future to either the result, or any exception that was thrown. */
fun <T> SettableFuture<T>.setFrom(logger: Logger? = null, block: () -> T): SettableFuture<T> {
    try {
        set(block())
    } catch (e: Exception) {
        logger?.error("Caught exception", e)
        setException(e)
    }
    return this
}

// Simple infix function to add back null safety that the JDK lacks:  timeA until timeB
infix fun Temporal.until(endExclusive: Temporal) = Duration.between(this, endExclusive)

/** Returns the index of the given item or throws [IllegalArgumentException] if not found. */
fun <T> List<T>.indexOfOrThrow(item: T): Int {
    val i = indexOf(item)
    require(i != -1)
    return i
}

/**
 * Returns the single element matching the given [predicate], or `null` if element was not found,
 * or throws if more than one element was found.
 */
fun <T> Iterable<T>.noneOrSingle(predicate: (T) -> Boolean): T? {
    var single: T? = null
    for (element in this) {
        if (predicate(element)) {
            if (single == null) {
                single = element
            } else throw IllegalArgumentException("Collection contains more than one matching element.")
        }
    }
    return single
}

/** Returns single element, or `null` if element was not found, or throws if more than one element was found. */
fun <T> Iterable<T>.noneOrSingle(): T? {
    var single: T? = null
    for (element in this) {
        if (single == null) {
            single = element
        } else throw IllegalArgumentException("Collection contains more than one matching element.")
    }
    return single
}

// An alias that can sometimes make code clearer to read.
val RunOnCallerThread = MoreExecutors.directExecutor()

fun <T> logElapsedTime(label: String, logger: Logger? = null, body: () -> T): T {
    val now = System.currentTimeMillis()
    val r = body()
    val elapsed = System.currentTimeMillis() - now
    if (logger != null)
        logger.info("$label took $elapsed msec")
    else
        println("$label took $elapsed msec")
    return r
}

/**
 * A threadbox is a simple utility that makes it harder to forget to take a lock before accessing some shared state.
 * Simply define a private class to hold the data that must be grouped under the same lock, and then pass the only
 * instance to the ThreadBox constructor. You can now use the [locked] method with a lambda to take the lock in a
 * way that ensures it'll be released if there's an exception.
 *
 * Note that this technique is not infallible: if you capture a reference to the fields in another lambda which then
 * gets stored and invoked later, there may still be unsafe multi-threaded access going on, so watch out for that.
 * This is just a simple guard rail that makes it harder to slip up.
 *
 * Example:
 *
 * private class MutableState { var i = 5 }
 * private val state = ThreadBox(MutableState())
 *
 * val ii = state.locked { i }
 */
class ThreadBox<out T>(content: T, val lock: ReentrantLock = ReentrantLock()) {
    val content = content
    inline fun <R> locked(body: T.() -> R): R = lock.withLock { body(content) }
    inline fun <R> alreadyLocked(body: T.() -> R): R {
        check(lock.isHeldByCurrentThread, { "Expected $lock to already be locked." })
        return body(content)
    }
}

/**
 * This represents a transient exception or condition that might no longer be thrown if the operation is re-run or called
 * again.
 *
 * We avoid the use of the word transient here to hopefully reduce confusion with the term in relation to (Java) serialization.
 */
abstract class RetryableException(message: String) : Exception(message)

/**
 * A simple wrapper that enables the use of Kotlin's "val x by TransientProperty { ... }" syntax. Such a property
 * will not be serialized to disk, and if it's missing (or the first time it's accessed), the initializer will be
 * used to set it up. Note that the initializer will be called with the TransientProperty object locked.
 */
class TransientProperty<out T>(private val initializer: () -> T) {
    @Transient private var v: T? = null

    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (v == null)
            v = initializer()
        return v!!
    }
}

/**
 * Given a path to a zip file, extracts it to the given directory.
 */
fun extractZipFile(zipPath: Path, toPath: Path) {
    val normalisedToPath = toPath.normalize()
    if (!Files.exists(normalisedToPath))
        Files.createDirectories(normalisedToPath)

    ZipInputStream(BufferedInputStream(Files.newInputStream(zipPath))).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            val outPath = normalisedToPath.resolve(e.name)

            // Security checks: we should reject a zip that contains tricksy paths that try to escape toPath.
            if (!outPath.normalize().startsWith(normalisedToPath))
                throw IllegalStateException("ZIP contained a path that resolved incorrectly: ${e.name}")

            if (e.isDirectory) {
                Files.createDirectories(outPath)
                continue
            }
            Files.newOutputStream(outPath).use { out ->
                ByteStreams.copy(zip, out)
            }
            zip.closeEntry()
        }
    }
}

// TODO: Generic csv printing utility for clases.
