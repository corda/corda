package net.corda.core.crypto

import java.nio.charset.Charset
import java.util.*
import javax.xml.bind.DatatypeConverter


// This file includes useful encoding methods and extension functions for the most common encoding/decoding operations.

// [ByteArray] encoders

/** Convert a byte array to a base 58 encoded string.*/
fun ByteArray.toBase58(): String =
        Base58.encode(this)

/** Convert a byte array to a base 64 encoded string.*/
fun ByteArray.toBase64(): String =
        Base64.getEncoder().encodeToString(this)

/** Convert a byte array to a hex (base 16) capitalised encoded string.*/
fun ByteArray.toHex(): String =
    DatatypeConverter.printHexBinary(this)


// [String] encoders and decoders

/** Base58-String to the actual real [String] */
fun String.base58ToRealString() =
        String(base58ToByteArray(), Charset.defaultCharset())

/** Base64-String to the actual real [String] */
fun String.base64ToRealString() =
        String(base64ToByteArray())

/** Hex-String to the actual real [String] */
fun String.hexToRealString() =
        String(hexToByteArray())

/** Base58-String to [ByteArray]. */
fun String.base58ToByteArray(): ByteArray =
        Base58.decode(this)

/** Base64-String to [ByteArray]. */
fun String.base64ToByteArray(): ByteArray =
        Base64.getDecoder().decode(this)

/** Hex-String to [ByteArray]. Accept lowercase or capital or mixed letters. */
fun String.hexToByteArray(): ByteArray =
        DatatypeConverter.parseHexBinary(this);


// Helper vars.
private val HEX_ALPHABET = "0123456789ABCDEF".toCharArray()
