/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import com.google.common.io.BaseEncoding
import core.serialization.SerializeableWithKryo
import java.time.Duration
import java.util.*

/** A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect */
open class OpaqueBytes(val bits: ByteArray) : SerializeableWithKryo {
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
