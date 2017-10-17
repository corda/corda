@file:JvmName("ByteArrays")

package net.corda.core.utilities

import net.corda.core.serialization.CordaSerializable
import java.io.ByteArrayInputStream
import javax.xml.bind.DatatypeConverter

/**
 * An abstraction of a byte array, with offset and size that does no copying of bytes unless asked to.
 *
 * The data of interest typically starts at position [offset] within the [bytes] and is [size] bytes long.
 */
@CordaSerializable
sealed class ByteSequence : Comparable<ByteSequence> {
    /**
     * The underlying bytes.
     */
    abstract val bytes: ByteArray
    /**
     * The number of bytes this sequence represents.
     */
    abstract val size: Int
    /**
     * The start position of the sequence within the byte array.
     */
    abstract val offset: Int

    /** Returns a [ByteArrayInputStream] of the bytes */
    fun open() = ByteArrayInputStream(bytes, offset, size)

    /**
     * Create a sub-sequence backed by the same array.
     *
     * @param offset The offset within this sequence to start the new sequence.  Note: not the offset within the backing array.
     * @param size The size of the intended sub sequence.
     */
    fun subSequence(offset: Int, size: Int): ByteSequence {
        require(offset >= 0)
        require(offset + size <= this.size)
        return if (offset == 0 && size == this.size) this else of(bytes, this.offset + offset, size)
    }

    companion object {
        /**
         * Construct a [ByteSequence] given a [ByteArray] and optional offset and size, that represents that potentially
         * sub-sequence of bytes.  The returned implementation is optimised when the whole [ByteArray] is the sequence.
         */
        @JvmStatic
        @JvmOverloads
        fun of(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): ByteSequence {
            return if (offset == 0 && size == bytes.size && size != 0) OpaqueBytes(bytes) else OpaqueBytesSubSequence(bytes, offset, size)
        }
    }

    /**
     * Take the first n bytes of this sequence as a sub-sequence.  See [subSequence] for further semantics.
     */
    fun take(n: Int): ByteSequence {
        require(size >= n)
        return subSequence(0, n)
    }

    /**
     * Copy this sequence, complete with new backing array.  This can be helpful to break references to potentially
     * large backing arrays from small sub-sequences.
     */
    fun copy(): ByteSequence = of(bytes.copyOfRange(offset, offset + size))

    /**
     * Compare byte arrays byte by byte.  Arrays that are shorter are deemed less than longer arrays if all the bytes
     * of the shorter array equal those in the same position of the longer array.
     */
    override fun compareTo(other: ByteSequence): Int {
        val min = minOf(this.size, other.size)
        // Compare min bytes
        for (index in 0 until min) {
            val unsignedThis = java.lang.Byte.toUnsignedInt(this.bytes[this.offset + index])
            val unsignedOther = java.lang.Byte.toUnsignedInt(other.bytes[other.offset + index])
            if (unsignedThis != unsignedOther) {
                return Integer.signum(unsignedThis - unsignedOther)
            }
        }
        // First min bytes is the same, so now resort to size
        return Integer.signum(this.size - other.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteSequence) return false
        if (this.size != other.size) return false
        return subArraysEqual(this.bytes, this.offset, this.size, other.bytes, other.offset)
    }

    private fun subArraysEqual(a: ByteArray, aOffset: Int, length: Int, b: ByteArray, bOffset: Int): Boolean {
        var bytesRemaining = length
        var aPos = aOffset
        var bPos = bOffset
        while (bytesRemaining-- > 0) {
            if (a[aPos++] != b[bPos++]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for (index in offset until (offset + size)) {
            result = 31 * result + bytes[index]
        }
        return result
    }

    override fun toString(): String = "[${bytes.copyOfRange(offset, offset + size).toHexString()}]"
}

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
open class OpaqueBytes(bytes: ByteArray) : ByteSequence() {
    companion object {
        /**
         * Create [OpaqueBytes] from a sequence of [Byte] values.
         */
        @JvmStatic
        fun of(vararg b: Byte) = OpaqueBytes(byteArrayOf(*b))
    }

    init {
        require(bytes.isNotEmpty())
    }

    /**
     * The bytes are always cloned so that this object becomes immutable. This has been done
     * to prevent tampering with entities such as [SecureHash] and [PrivacySalt], as well as
     * preserve the integrity of our hash constants [zeroHash] and [allOnesHash].
     *
     * Cloning like this may become a performance issue, depending on whether or not the JIT
     * compiler is ever able to optimise away the clone. In which case we may need to revisit
     * this later.
     */
    override final val bytes: ByteArray = bytes
        get() = field.clone()
    override val size: Int = bytes.size
    override val offset: Int = 0
}

/**
 * Copy [size] bytes from this [ByteArray] starting from [offset] into a new [ByteArray].
 */
fun ByteArray.sequence(offset: Int = 0, size: Int = this.size) = ByteSequence.of(this, offset, size)

/**
 * Converts this [ByteArray] into a [String] of hexadecimal digits.
 */
fun ByteArray.toHexString(): String = DatatypeConverter.printHexBinary(this)

/**
 * Converts this [String] of hexadecimal digits into a [ByteArray].
 * @throws IllegalArgumentException if the [String] contains incorrectly-encoded characters.
 */
fun String.parseAsHex(): ByteArray = DatatypeConverter.parseHexBinary(this)

/**
 * Class is public for serialization purposes
 */
class OpaqueBytesSubSequence(override val bytes: ByteArray, override val offset: Int, override val size: Int) : ByteSequence() {
    init {
        require(offset >= 0 && offset < bytes.size)
        require(size >= 0 && size <= bytes.size)
    }
}
