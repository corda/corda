@file:JvmName("ByteUtils")
package net.corda.attestation

fun byteArray(size: Int, of: Int) = ByteArray(size, { of.toByte() })

fun unsignedByteArrayOf(vararg values: Int) = ByteArray(values.size).apply {
    for (i in 0 until values.size) {
        this[i] = values[i].toByte()
    }
}
