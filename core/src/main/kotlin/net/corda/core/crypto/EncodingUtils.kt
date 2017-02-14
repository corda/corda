package net.corda.core.crypto

import java.util.*

/**
 * This file includes useful encoding methods and extension functions for the most common encoding/decoding operations.
 */

/**
 * [ByteArray]
 */

// Base58.
fun ByteArray.toBase58(): String =
        Base58.encode(this)

// Base64.
fun ByteArray.toBase64(): String =
        Base64.getEncoder().encodeToString(this)

// Hex (or Base16).
fun ByteArray.toHex(): String {
    val result = StringBuffer()
    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_ALPHABET[firstIndex])
        result.append(HEX_ALPHABET[secondIndex])
    }
    return result.toString()
}

/**
 * [String]
 */

// Base58-String to the actual real [String] using the UTF-8 character set.
fun String.base58ToRealString() = String(base58ToByteArray())

// Base64-String to the actual real [String] using the UTF-8 character set.
fun String.base64ToRealString() = String(base64ToByteArray())

// Hex-String to the actual real [String] using the UTF-8 character set.
fun String.hexToRealString() = String(hexToByteArray())

// Base58-String to [ByteArray].
fun String.base58ToByteArray(): ByteArray {
    try {
        return Base58.decode(this)
    } catch (afe: AddressFormatException) {
        throw afe
    }
}

// Base64-String to [ByteArray].
fun String.base64ToByteArray(): ByteArray {
    try {
        return Base64.getDecoder().decode(this)
    } catch (iae: IllegalArgumentException) {
        throw iae
    }
}

// Hex-String to [ByteArray]. Accept capital letters only.
fun String.hexToByteArray(): ByteArray {
    if (this.length == 0) {
        return ByteArray(0)
    } else if (this.matches(HEX_REGEX)) {
        val result = ByteArray(length / 2)
        for (i in 0 until length step 2) {
            val firstIndex = HEX_ALPHABET.indexOf(this[i]);
            val secondIndex = HEX_ALPHABET.indexOf(this[i + 1]);

            val octet = firstIndex.shl(4).or(secondIndex)
            result.set(i.shr(1), octet.toByte())
        }
        return result
    } else
        throw IllegalArgumentException()
}

/**
 * Helper vars.
 */

private val HEX_REGEX = Regex("-?[0-9A-F]+") // accept capital letters only
private val HEX_ALPHABET = "0123456789ABCDEF".toCharArray()
