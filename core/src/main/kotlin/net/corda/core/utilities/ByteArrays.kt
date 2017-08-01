@file:JvmName("ByteArrays")

package net.corda.core.utilities

import com.google.common.io.BaseEncoding
import net.corda.core.serialization.CordaSerializable
import java.io.ByteArrayInputStream
import java.util.*

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
@CordaSerializable
open class OpaqueBytes(val bytes: ByteArray) {
    companion object {
        @JvmStatic
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    init {
        check(bytes.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpaqueBytes) return false
        return Arrays.equals(bytes, other.bytes)
    }

    override fun hashCode() = Arrays.hashCode(bytes)
    override fun toString() = "[" + bytes.toHexString() + "]"

    val size: Int get() = bytes.size

    /** Returns a [ByteArrayInputStream] of the bytes */
    fun open() = ByteArrayInputStream(bytes)
}

fun ByteArray.opaque(): OpaqueBytes = OpaqueBytes(this)
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)
fun String.parseAsHex(): ByteArray = BaseEncoding.base16().decode(this)
