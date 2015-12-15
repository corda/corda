/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import com.google.common.io.BaseEncoding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.slf4j.Logger
import java.security.SecureRandom
import java.time.Duration
import java.util.*
import java.util.concurrent.Executor

/** A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect */
open class OpaqueBytes(val bits: ByteArray) {
    init { check(bits.isNotEmpty()) }

    companion object {
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other !is OpaqueBytes) return false
        return Arrays.equals(bits, other.bits)
    }

    override fun hashCode() = Arrays.hashCode(bits)
    override fun toString() = "[" + BaseEncoding.base16().encode(bits) + "]"

    val size: Int get() = bits.size
}

fun ByteArray.opaque(): OpaqueBytes = OpaqueBytes(this)

val Int.days: Duration get() = Duration.ofDays(this.toLong())
val Int.hours: Duration get() = Duration.ofHours(this.toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())

/**
 * Returns a random positive long generated using a secure RNG. This function sacrifies a bit of entropy in order to
 * avoid potential bugs where the value is used in a context where negative numbers are not expected.
 */
fun random63BitValue(): Long = Math.abs(SecureRandom.getInstanceStrong().nextLong())

fun <T> ListenableFuture<T>.whenComplete(executor: Executor? = null, body: () -> Unit) {
    addListener(Runnable { body() }, executor ?: MoreExecutors.directExecutor())
}

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