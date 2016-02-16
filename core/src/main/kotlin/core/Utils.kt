/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.slf4j.Logger
import java.security.SecureRandom
import java.time.Duration
import java.time.temporal.Temporal
import java.util.concurrent.Executor
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val Int.days: Duration get() = Duration.ofDays(this.toLong())
val Int.hours: Duration get() = Duration.ofHours(this.toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())

/**
 * Returns a random positive long generated using a secure RNG. This function sacrifies a bit of entropy in order to
 * avoid potential bugs where the value is used in a context where negative numbers are not expected.
 */
fun random63BitValue(): Long = Math.abs(SecureRandom.getInstanceStrong().nextLong())

// Some utilities for working with Guava listenable futures.
fun <T> ListenableFuture<T>.then(executor: Executor, body: () -> Unit) = addListener(Runnable(body), executor)
fun <T> ListenableFuture<T>.success(executor: Executor, body: (T) -> Unit) = then(executor) {
    val r = try { get() } catch(e: Throwable) { return@then }
    body(r)
}
fun <T> ListenableFuture<T>.failure(executor: Executor, body: (Throwable) -> Unit) = then(executor) {
    try { get() } catch(e: Throwable) { body(e) }
}
infix fun <T> ListenableFuture<T>.then(body: () -> Unit): ListenableFuture<T> = apply { then(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.success(body: (T) -> Unit): ListenableFuture<T> = apply { success(RunOnCallerThread, body) }
infix fun <T> ListenableFuture<T>.failure(body: (Throwable) -> Unit): ListenableFuture<T> = apply { failure(RunOnCallerThread, body) }


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

// An alias that can sometimes make code clearer to read.
val RunOnCallerThread = MoreExecutors.directExecutor()

inline fun <T> logElapsedTime(label: String, logger: Logger? = null, body: () -> T): T {
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
class ThreadBox<T>(content: T, private val lock: Lock = ReentrantLock()) {
    private val content = content
    fun <R> locked(body: T.() -> R): R = lock.withLock { body(content) }
}
