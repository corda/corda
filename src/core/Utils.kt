package core

import com.google.common.io.BaseEncoding
import java.util.*

/** A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect */
open class OpaqueBytes(val bits: ByteArray) {
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