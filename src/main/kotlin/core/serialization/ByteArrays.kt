/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import com.google.common.io.BaseEncoding
import core.SecureHash
import core.sha256
import java.util.*

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
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

class SerializedBytes<T : Any>(bits: ByteArray) : OpaqueBytes(bits) {
    @Transient val hash: SecureHash by lazy { bits.sha256() }
}

fun ByteArray.opaque(): OpaqueBytes = OpaqueBytes(this)
inline fun <reified T : Any> SerializedBytes<T>.deserialize(): T = bits.deserialize()
