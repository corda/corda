package net.corda.attestation

import org.junit.Assert.*
import org.junit.Test
import sun.security.ec.ECPublicKeyImpl
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint

class EndianUtilsTest {
    @Test
    fun testBigIntegerToLittleEndian() {
        val source = BigInteger("8877665544332211", 16)
        assertArrayEquals(unsignedByteArrayOf(0x11), source.toLittleEndian(1))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22), source.toLittleEndian(2))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33), source.toLittleEndian(3))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33, 0x44), source.toLittleEndian(4))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55), source.toLittleEndian(5))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66), source.toLittleEndian(6))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77), source.toLittleEndian(7))
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88), source.toLittleEndian(8))
    }

    @Test
    fun testRemovingLeadingZeros() {
        assertArrayEquals(byteArrayOf(), byteArrayOf().removeLeadingZeros())
        assertArrayEquals(byteArrayOf(), byteArrayOf(0x00, 0x00, 0x00, 0x00).removeLeadingZeros())
        assertArrayEquals(byteArrayOf(0x7F, 0x63), byteArrayOf(0x00, 0x00, 0x7F, 0x63).removeLeadingZeros())
        assertArrayEquals(byteArrayOf(0x7F, 0x43), byteArrayOf(0x7F, 0x43).removeLeadingZeros())
    }

    @Test
    fun testUnsignedBytes() {
        val source = BigInteger("FFAA", 16)
        assertArrayEquals(unsignedByteArrayOf(0xFF, 0xAA), source.toUnsignedBytes())
    }

    @Test
    fun testToHexString() {
        val source = unsignedByteArrayOf(0xFF, 0xAA, 0x00)
        assertEquals("FFAA00", source.toHexString().toUpperCase())
    }

    @Test
    fun testShortToLittleEndian() {
        assertArrayEquals(unsignedByteArrayOf(0x0F, 0x00), 15.toShort().toLittleEndian())
        assertArrayEquals(unsignedByteArrayOf(0xFF, 0xFF), 65535.toShort().toLittleEndian())
        assertArrayEquals(unsignedByteArrayOf(0x00, 0x01), 256.toShort().toLittleEndian())
    }

    @Test
    fun testLittleEndianPublicKey() {
        val ecParameters = (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey).params
        val publicKey = ECPublicKeyImpl(ECPoint(BigInteger.TEN, BigInteger.ONE), ecParameters)

        val littleEndian2 = publicKey.toLittleEndian(2)
        assertArrayEquals(byteArrayOf(0x0A, 0x01), littleEndian2)
        val littleEndian4 = publicKey.toLittleEndian(4)
        assertArrayEquals(byteArrayOf(0x0A, 0x00, 0x01, 0x00), littleEndian4)
        val littleEndian8 = publicKey.toLittleEndian(8)
        assertArrayEquals(byteArrayOf(0x0A, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00), littleEndian8)
    }

    @Test
    fun testLittleEndianUnsigned() {
        val veryBigNumber1 = BigInteger("FFFFFFFFEEEEEEEE", 16)
        val veryBigNumber2 = BigInteger("AAAAAAAABBBBBBBB", 16)
        val ecParameters = (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey).params
        val publicKey = ECPublicKeyImpl(ECPoint(veryBigNumber1, veryBigNumber2), ecParameters)

        val littleEndian16 = publicKey.toLittleEndian(16)
        assertArrayEquals(byteArray(4, 0xEE)
                .plus(byteArray(4, 0xFF)
                        .plus(byteArray(4, 0xBB))
                        .plus(byteArray(4, 0xAA))), littleEndian16)
    }

    @Test
    fun testLittleEndianUnderflow() {
        val number = BigInteger("007FEDCB", 16)
        val littleEndian = number.toLittleEndian(4)
        assertArrayEquals(unsignedByteArrayOf(0xcb, 0xed, 0x7f, 0x00), littleEndian)
    }
}
