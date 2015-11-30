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
}

val Int.days: Duration get() = Duration.ofDays(this.toLong())
val Int.hours: Duration get() = Duration.ofHours(this.toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
