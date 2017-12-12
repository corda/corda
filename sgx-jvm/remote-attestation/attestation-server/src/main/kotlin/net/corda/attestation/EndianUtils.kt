@file:JvmName("EndianUtils")
package net.corda.attestation

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.KeySpec

const val KEY_SIZE = 64

fun ByteArray.removeLeadingZeros(): ByteArray {
    if (isEmpty() || this[0] != 0.toByte()) { return this }
    for (i in 1 until size) {
        if (this[i] != 0.toByte()) {
            return copyOfRange(i, size)
        }
    }
    return byteArrayOf()
}

fun ByteArray.toPositiveInteger() = BigInteger(1, this)
fun ByteArray.toHexString(): String = toPositiveInteger().toString(16)

fun BigInteger.toLittleEndian(size: Int = KEY_SIZE) = ByteArray(size).apply {
    val leBytes = toByteArray().reversedArray()
    System.arraycopy(leBytes, 0, this, 0, Math.min(size, leBytes.size))
}

fun BigInteger.toUnsignedBytes(): ByteArray = toByteArray().removeLeadingZeros()

fun String.hexToBytes(): ByteArray = BigInteger(this, 16).toUnsignedBytes()

fun ByteArray.toBigEndianKeySpec(ecParameters: ECParameterSpec): KeySpec {
    if (size != KEY_SIZE) {
        throw IllegalArgumentException("Public key has incorrect size ($size bytes)")
    }
    val ecPoint = ECPoint(
        copyOf(size / 2).reversedArray().toPositiveInteger(),
        copyOfRange(size / 2, size).reversedArray().toPositiveInteger()
    )
    return ECPublicKeySpec(ecPoint, ecParameters)
}

fun ECPublicKey.toLittleEndian(size: Int = KEY_SIZE): ByteArray {
    val x = w.affineX.toByteArray().reversedArray()
    val y = w.affineY.toByteArray().reversedArray()
    return ByteArray(size).apply {
        // Automatically discards any extra "most significant" last byte, which is assumed to be zero.
        val half = size / 2
        System.arraycopy(x, 0, this, 0, Math.min(half, x.size))
        System.arraycopy(y, 0, this, half, Math.min(half, y.size))
    }
}

fun Short.toLittleEndian(): ByteArray = ByteBuffer.allocate(2)
    .order(LITTLE_ENDIAN)
    .putShort(this)
    .array()
