@file:JvmName("ByteArrays")

package net.corda.core.utilities

import net.corda.annotations.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.lang.Math.max
import java.lang.Math.min
import java.nio.ByteBuffer
import javax.xml.bind.DatatypeConverter

/**
 * An abstraction of a byte array, with offset and size that does no copying of bytes unless asked to.
 *
 * The data of interest typically starts at position [offset] within the [bytes] and is [size] bytes long.
 *
 * @property offset The start position of the sequence within the byte array.
 * @property size The number of bytes this sequence represents.
 */
@Serializable
sealed class ByteSequence(private val _bytes: ByteArray, val offset: Int, val size: Int) : Comparable<ByteSequence> {
    /**
     * The underlying bytes.  Some implementations may choose to make a copy of the underlying [ByteArray] for
     * security reasons.  For example, [OpaqueBytes].
     */
    abstract val bytes: ByteArray

    /** Returns a [ByteArrayInputStream] of the bytes */
    fun open() = ByteArrayInputStream(_bytes, offset, size)

    /**
     * Create a sub-sequence, that may be backed by a new byte array.
     *
     * @param offset The offset within this sequence to start the new sequence.  Note: not the offset within the backing array.
     * @param size The size of the intended sub sequence.
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun subSequence(offset: Int, size: Int): ByteSequence {
        require(offset >= 0)
        require(offset + size <= this.size)
        // Intentionally use bytes rather than _bytes, to mirror the copy-or-not behaviour of that property.
        return if (offset == 0 && size == this.size) this else OpaqueBytesSubSequence(bytes, this.offset + offset, size)
    }

    companion object {
        /**
         * Construct a [ByteSequence] given a [ByteArray] and optional offset and size, that represents that potentially
         * sub-sequence of bytes.
         */
        @JvmStatic
        @JvmOverloads
        fun of(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): ByteSequence {
            return OpaqueBytesSubSequence(bytes, offset, size)
        }
    }

    /**
     * Take the first n bytes of this sequence as a sub-sequence.  See [subSequence] for further semantics.
     */
    fun take(n: Int): ByteSequence = subSequence(0, n)

    /**
     * A new read-only [ByteBuffer] view of this sequence or part of it.
     * If [start] or [end] are negative then [IllegalArgumentException] is thrown, otherwise they are clamped if necessary.
     * This method cannot be used to get bytes before [offset] or after [offset]+[size], and never makes a new array.
     */
    fun slice(start: Int = 0, end: Int = size): ByteBuffer {
        require(start >= 0)
        require(end >= 0)
        val clampedStart = min(start, size)
        val clampedEnd = min(end, size)
        return ByteBuffer.wrap(_bytes, offset + clampedStart, max(0, clampedEnd - clampedStart)).asReadOnlyBuffer()
    }

    /** Write this sequence to an [OutputStream]. */
    fun writeTo(output: OutputStream) = output.write(_bytes, offset, size)

    /** Write this sequence to a [ByteBuffer]. */
    fun putTo(buffer: ByteBuffer): ByteBuffer = buffer.put(_bytes, offset, size)

    /**
     * Copy this sequence, complete with new backing array.  This can be helpful to break references to potentially
     * large backing arrays from small sub-sequences.
     */
    fun copy(): ByteSequence = of(copyBytes())

    /** Same as [copy] but returns just the new byte array. */
    fun copyBytes(): ByteArray = _bytes.copyOfRange(offset, offset + size)

    /**
     * Compare byte arrays byte by byte.  Arrays that are shorter are deemed less than longer arrays if all the bytes
     * of the shorter array equal those in the same position of the longer array.
     */
    override fun compareTo(other: ByteSequence): Int {
        val min = minOf(this.size, other.size)
        val thisBytes = this._bytes
        val otherBytes = other._bytes
        // Compare min bytes
        for (index in 0 until min) {
            val unsignedThis = java.lang.Byte.toUnsignedInt(thisBytes[this.offset + index])
            val unsignedOther = java.lang.Byte.toUnsignedInt(otherBytes[other.offset + index])
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
        return subArraysEqual(this._bytes, this.offset, this.size, other._bytes, other.offset)
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
        val thisBytes = _bytes
        var result = 1
        for (index in offset until (offset + size)) {
            result = 31 * result + thisBytes[index]
        }
        return result
    }

    override fun toString(): String = "[${copyBytes().toHexString()}]"
}

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free. Project Valhalla is adding such
 * functionality to Java, but it won't arrive for a few years yet!
 */
open class OpaqueBytes(bytes: ByteArray) : ByteSequence(bytes, 0, bytes.size) {
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
     * to prevent tampering with entities such as [net.corda.core.crypto.SecureHash] and [net.corda.core.contracts.PrivacySalt], as well as
     * preserve the integrity of our hash constants [net.corda.core.crypto.SecureHash.zeroHash] and [net.corda.core.crypto.SecureHash.allOnesHash].
     *
     * Cloning like this may become a performance issue, depending on whether or not the JIT
     * compiler is ever able to optimise away the clone. In which case we may need to revisit
     * this later.
     */
    override final val bytes: ByteArray = bytes
        get() = field.clone()
}

/**
 * Wrap [size] bytes from this [ByteArray] starting from [offset] into a new [ByteArray].
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
class OpaqueBytesSubSequence(override val bytes: ByteArray, offset: Int, size: Int) : ByteSequence(bytes, offset, size) {
    init {
        require(offset >= 0 && offset < bytes.size)
        require(size >= 0 && size <= bytes.size)
    }
}
