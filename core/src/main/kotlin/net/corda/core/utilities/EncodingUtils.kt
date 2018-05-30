@file:JvmName("EncodingUtils")
@file:Deterministic

package net.corda.core.utilities

import net.corda.core.Deterministic
import net.corda.core.crypto.Base58
import net.corda.core.crypto.Crypto
import net.corda.core.internal.hash
import java.nio.charset.Charset
import java.security.PublicKey
import java.util.*
import javax.xml.bind.DatatypeConverter

// This file includes useful encoding methods and extension functions for the most common encoding/decoding operations.

/**
 * The maximum supported field-size for hash HEX-encoded outputs (e.g. database fields).
 * This value is enough to support hash functions with outputs up to 512 bits (e.g. SHA3-512), in which
 * case 128 HEX characters are required.
 * 130 was selected instead of 128, to allow for 2 extra characters that will be used as hash-scheme identifiers.
 */
const val MAX_HASH_HEX_SIZE = 130

// [ByteArray] encoders

/** Convert a byte array to a Base58 encoded [String]. */
fun ByteArray.toBase58(): String = Base58.encode(this)

/** Convert a byte array to a Base64 encoded [String]. */
fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

/** Convert a byte array to a hex (Base16) capitalized encoded [String]. */
fun ByteArray.toHex(): String = DatatypeConverter.printHexBinary(this)

// [String] encoders and decoders

/** Base58-String to the actual real [String], i.e. "JxF12TrwUP45BMd" -> "Hello World". */
fun String.base58ToRealString() = String(base58ToByteArray(), Charset.defaultCharset())

/** Base64-String to the actual real [String], i.e. "SGVsbG8gV29ybGQ=" -> "Hello World". */
fun String.base64ToRealString() = String(base64ToByteArray())

/** HEX-String to the actual real [String], i.e. "48656C6C6F20576F726C64" -> "Hello World". */
fun String.hexToRealString() = String(hexToByteArray())

fun String.base58ToByteArray(): ByteArray = Base58.decode(this)

fun String.base64ToByteArray(): ByteArray = Base64.getDecoder().decode(this)

/** Hex-String to [ByteArray]. Accept any hex form (capitalized, lowercase, mixed). */
fun String.hexToByteArray(): ByteArray = DatatypeConverter.parseHexBinary(this)

// Encoding changers

/** Encoding changer. Base58-[String] to Base64-[String], i.e. "SGVsbG8gV29ybGQ=" -> JxF12TrwUP45BMd" */
fun String.base58toBase64(): String = base58ToByteArray().toBase64()

/** Encoding changer. Base58-[String] to Hex-[String], i.e. "SGVsbG8gV29ybGQ=" -> "48656C6C6F20576F726C64" */
fun String.base58toHex(): String = base58ToByteArray().toHex()

/** Encoding changer. Base64-[String] to Base58-[String], i.e. "SGVsbG8gV29ybGQ=" -> JxF12TrwUP45BMd" */
fun String.base64toBase58(): String = base64ToByteArray().toBase58()

/** Encoding changer. Base64-[String] to Hex-[String], i.e. "SGVsbG8gV29ybGQ=" -> "48656C6C6F20576F726C64" */
fun String.base64toHex(): String = base64ToByteArray().toHex()

/** Encoding changer. Hex-[String] to Base58-[String], i.e. "48656C6C6F20576F726C64" -> "JxF12TrwUP45BMd" */
fun String.hexToBase58(): String = hexToByteArray().toBase58()

/** Encoding changer. Hex-[String] to Base64-[String], i.e. "48656C6C6F20576F726C64" -> "SGVsbG8gV29ybGQ=" */
fun String.hexToBase64(): String = hexToByteArray().toBase64()

// TODO We use for both CompositeKeys and EdDSAPublicKey custom serializers and deserializers. We need to specify encoding.
// TODO: follow the crypto-conditions ASN.1 spec, some changes are needed to be compatible with the condition
//       structure, e.g. mapping a PublicKey to a condition with the specific feature (ED25519).
/**
 * Method to return the [PublicKey] object given its Base58-[String] representation.
 * @param base58String the Base58 encoded format of the serialised [PublicKey].
 * @return the resulted [PublicKey] after decoding the [base58String] input and then deserialising to a [PublicKey] object.
 */
fun parsePublicKeyBase58(base58String: String): PublicKey = Crypto.decodePublicKey(base58String.base58ToByteArray())

/** Return the Base58 representation of the serialised public key. */
fun PublicKey.toBase58String(): String = this.encoded.toBase58()

/** Return the bytes of the SHA-256 output for this public key. */
fun PublicKey.toSHA256Bytes(): ByteArray = this.hash.bytes
