package net.corda.attestation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets.*
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey

class CryptoTest {
    private lateinit var keyFactory: KeyFactory
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Before
    fun setup() {
        keyFactory = KeyFactory.getInstance("EC")
        crypto = cryptoProvider.crypto
    }

    @Test
    fun testKeyConversions() {
        val keyPair = crypto.generateKeyPair()
        val ecPublicKey = keyPair.public as ECPublicKey
        val ecParameters = ecPublicKey.params
        val bytes = ecPublicKey.toLittleEndian()
        val resultKey = keyFactory.generatePublic(bytes.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        assertEquals(ecPublicKey, resultKey)
        assertArrayEquals(ecPublicKey.encoded, resultKey.encoded)
    }

    @Test
    fun testSharedSecret() {
        val keyPairA = crypto.generateKeyPair()
        val keyPairB = crypto.generateKeyPair()

        val secretA = crypto.generateSharedSecret(keyPairA.private, keyPairB.public)
        val secretB = crypto.generateSharedSecret(keyPairB.private, keyPairA.public)
        assertArrayEquals(secretB, secretA)
        assertEquals(32, secretA.size)
    }

    @Test
    fun testEncryption() {
        val keyPairA = crypto.generateKeyPair()
        val keyPairB = crypto.generateKeyPair()
        val secretKeyA = crypto.generateSecretKey(keyPairA.private, keyPairB.public)
        val secretKeyB = crypto.generateSecretKey(keyPairB.private, keyPairA.public)
        assertEquals(secretKeyA, secretKeyB)
        assertArrayEquals(secretKeyA.encoded, secretKeyB.encoded)

        val iv = crypto.createIV()
        val data = crypto.encrypt("Sooper secret string value!".toByteArray(), secretKeyA, iv)
        assertEquals("Sooper secret string value!", String(crypto.decrypt(data, secretKeyB, iv), UTF_8))
    }

    @Test
    fun testAesCMAC() {
        val messageBytes = "Hello World".toByteArray()
        val keyBytes = "0123456789012345".toByteArray()
        val cmac = crypto.aesCMAC(keyBytes, messageBytes)
        assertArrayEquals("3AFAFFFC4EB9274ABD6C9CC3D8B6984A".hexToBytes(), cmac)
    }

    @Test
    fun testToHexArrayString() {
        val bytes = unsignedByteArrayOf(0xf5, 0x04, 0x83, 0x71)
        assertEquals("[0xf5,0x04,0x83,0x71]", bytes.toHexArrayString())
    }

    @Test
    fun `test vectors from RFC4493 and NIST 800-38B`() {
        // Key as defined on https://tools.ietf.org/html/rfc4493.html#appendix-A
        val testKey = "2b7e151628aed2a6abf7158809cf4f3c".hexToBytes()

        // Example 1: len = 0
        val out0 = crypto.aesCMAC(testKey, ByteArray(0))
        assertArrayEquals("bb1d6929e95937287fa37d129b756746".hexToBytes(), out0)

        // Example 2: len = 16
        val out16 = crypto.aesCMAC(testKey, "6bc1bee22e409f96e93d7e117393172a".hexToBytes())
        assertArrayEquals("070a16b46b4d4144f79bdd9dd04a287c".hexToBytes(), out16)

        // Example 3: len = 40
        val messageBytes40 = ("6bc1bee22e409f96e93d7e117393172a" +
                "ae2d8a571e03ac9c9eb76fac45af8e51" +
                "30c81c46a35ce411").hexToBytes()
        val out40 = crypto.aesCMAC(testKey, messageBytes40)
        assertArrayEquals("dfa66747de9ae63030ca32611497c827".hexToBytes(), out40)

        // Example 4: len = 64
        val messageBytes64 = ("6bc1bee22e409f96e93d7e117393172a" +
                "ae2d8a571e03ac9c9eb76fac45af8e51" +
                "30c81c46a35ce411e5fbc1191a0a52ef" +
                "f69f2445df4f9b17ad2b417be66c3710").hexToBytes()
        val out64 = crypto.aesCMAC(testKey, messageBytes64)
        assertArrayEquals("51f0bebf7e3b9d92fc49741779363cfe".hexToBytes(), out64)
    }
}
