package net.corda.core.crypto

import net.corda.core.utilities.hexToByteArray
import net.corda.core.utilities.toHexString
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecureHashTest {

    private val text = "CORDA DLT"
    private val textBytes = text.toByteArray()
    private val textSHA256 = "CA45C38A9343DB867D68C44E0159A0CEFF9809D505A98AFBF2FE66C3D0DE6309"
    private val textSHA256Bytes = textSHA256.hexToByteArray()
    private val textSHA512 = "EAB53A15394843FB096BDABAF909DC608F13DD47527AC21773CD13538EA2C04903FF2C3D8D9BAF046C876A821B8C1760A5B8EE20CB4782ECD98935E0D2F1D60C"
    private val textSHA512Bytes = textSHA512.hexToByteArray()

    @Test
    fun `Correct hash outputs`() {
        val javaSHA256 = MessageDigest.getInstance("SHA-256").digest(textBytes)
        assertEquals(javaSHA256.toHexString(), textSHA256)

        val javaSHA512 = MessageDigest.getInstance("SHA-512").digest(textBytes)
        assertEquals(javaSHA512.toHexString(), textSHA512)

        val sha256OfText = SecureHash.hash(textBytes, SecureHash.SHA256_ALGORITHM)
        assertEquals(textSHA256, sha256OfText.toString())

        val sha512OfText = SecureHash.hash(textBytes, SecureHash.SHA512_ALGORITHM)
        assertEquals(textSHA512, sha512OfText.toString())

        // Double hashing.
        val javaDoubleSHA256 = MessageDigest.getInstance("SHA-256").digest(javaSHA256)
        val doubleSha256OfText = SecureHash.hashTwice(textBytes, SecureHash.SHA256_ALGORITHM)
        val doubleSha256OfTextV2 = SecureHash.hash(textSHA256Bytes, SecureHash.SHA256_ALGORITHM)
        assertEquals(javaDoubleSHA256.toHexString(), doubleSha256OfText.toString())
        assertEquals(doubleSha256OfTextV2, doubleSha256OfText)

        val javaDoubleSHA512 = MessageDigest.getInstance("SHA-512").digest(javaSHA512)
        val doubleSha512OfText = SecureHash.hashTwice(textBytes, SecureHash.SHA512_ALGORITHM)
        val doubleSha512OfTextV2 = SecureHash.hash(textSHA512Bytes, SecureHash.SHA512_ALGORITHM)
        assertEquals(javaDoubleSHA512.toHexString(), doubleSha512OfText.toString())
        assertEquals(doubleSha512OfTextV2, doubleSha512OfText)

        // Hash concat.
        val javaSHA256Concat = MessageDigest.getInstance("SHA-256").digest(javaSHA256 + javaSHA256)
        val sha256OfTextConcat = sha256OfText.hashConcat(sha256OfText)
        assertEquals(javaSHA256Concat.toHexString(), sha256OfTextConcat.toString())

        val javaSHA512Concat = MessageDigest.getInstance("SHA-512").digest(javaSHA512 + javaSHA512)
        val sha512OfTextConcat = sha512OfText.hashConcat(sha512OfText)
        assertEquals(javaSHA512Concat.toHexString(), sha512OfTextConcat.toString())

        // Try to concat two different hash types.
        assertFailsWith<IllegalArgumentException> { sha256OfText.hashConcat(sha512OfText) }
        assertFailsWith<IllegalArgumentException> { sha512OfText.hashConcat(sha256OfText) }
    }

    @Test
    fun `default algorithm checks`() {
        // Current default algorithm is SHA256.
        val sha256OfText = SecureHash.hash(textBytes, SecureHash.SHA256_ALGORITHM)
        val defaultHashOfText = SecureHash.hash(textBytes)
        assertEquals(sha256OfText, defaultHashOfText)

        val sha256Object = SecureHash.parse(textSHA256)
        assertTrue(sha256Object is SecureHash.SHA256)

        val sha512Object = SecureHash.parse(textSHA512)
        assertTrue(sha512Object is SecureHash.SHA512)

        // Try to parse a non 32 or 64 bytes value.
        assertFailsWith<IllegalArgumentException> { SecureHash.parse(textSHA512 + textSHA256) }
    }
}
