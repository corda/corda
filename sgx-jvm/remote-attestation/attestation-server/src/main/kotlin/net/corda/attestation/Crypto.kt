package net.corda.attestation

import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.*
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.Cipher.*
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Crypto(val random: SecureRandom = SecureRandom.getInstance("NativePRNGNonBlocking")) {
    internal companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val macBlockSize = 16
        private const val gcmIvLength = 12
        const val gcmTagLength = 16

        @JvmStatic
        private val log = LoggerFactory.getLogger(Crypto::class.java)
        @JvmStatic
        private val smkValue = byteArrayOf(
                0x01,
                'S'.toAscii(),
                'M'.toAscii(),
                'K'.toAscii(),
                0x00,
                0x80.toByte(),
                0x00
        )
        @JvmStatic
        private val mkValue = byteArrayOf(
                0x01,
                'M'.toAscii(),
                'K'.toAscii(),
                0x00,
                0x80.toByte(),
                0x00
        )
        @JvmStatic
        private val skValue = byteArrayOf(
                0x01,
                'S'.toAscii(),
                'K'.toAscii(),
                0x00,
                0x80.toByte(),
                0x00
        )

        private fun Char.toAscii() = toString().toByteArray(US_ASCII)[0]
    }

    private val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance("EC")

    init {
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), random)
    }

    fun generateKeyPair(): KeyPair = keyPairGenerator.generateKeyPair()

    fun aesCMAC(key: ByteArray = ByteArray(macBlockSize), value: ByteArray): ByteArray = aesCMAC(key, { aes -> aes.update(value) })

    fun aesCMAC(key: ByteArray, update: (aes: Mac) -> Unit): ByteArray = Mac.getInstance("AESCMAC").let { aes ->
        aes.init(SecretKeySpec(key, "AES"))
        update(aes)
        aes.doFinal()
    }

    private fun createGCMParameters(iv: ByteArray) = GCMParameterSpec(gcmTagLength * 8, iv)

    fun createIV(): ByteArray = ByteArray(gcmIvLength).apply { random.nextBytes(this) }

    fun encrypt(data: ByteArray, secretKey: SecretKey, secretIV: ByteArray): ByteArray = Cipher.getInstance(AES_ALGORITHM).let { cip ->
        cip.init(ENCRYPT_MODE, secretKey, createGCMParameters(secretIV), random)
        cip.doFinal(data)
    }

    @Suppress("UNUSED")
    fun decrypt(data: ByteArray, secretKey: SecretKey, secretIV: ByteArray): ByteArray = Cipher.getInstance(AES_ALGORITHM).let { cip ->
        cip.init(DECRYPT_MODE, secretKey, createGCMParameters(secretIV))
        cip.doFinal(data)
    }

    fun generateSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        return KeyAgreement.getInstance("ECDH").let { ka ->
            ka.init(privateKey, random)
            ka.doPhase(peerPublicKey, true)
            ka.generateSecret()
        }
    }

    private fun generateKDK(sharedSecret: ByteArray)
            = aesCMAC(ByteArray(macBlockSize), sharedSecret.reversedArray()).apply { log.debug("KDK: {}", toHexArrayString()) }

    private fun generateSMK(sharedSecret: ByteArray)
            = aesCMAC(generateKDK(sharedSecret), smkValue).apply { log.debug("SMK: {}", toHexArrayString()) }

    fun generateSMK(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray
            = generateSMK(generateSharedSecret(privateKey, peerPublicKey))

    private fun generateMK(sharedSecret: ByteArray)
            = aesCMAC(generateKDK(sharedSecret), mkValue).apply { log.debug("MK: {}", toHexArrayString()) }

    fun generateMK(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray
            = generateMK(generateSharedSecret(privateKey, peerPublicKey))

    private fun generateSK(sharedSecret: ByteArray)
            = aesCMAC(generateKDK(sharedSecret), skValue).apply { log.debug("SK: {}", toHexArrayString()) }

    fun generateSecretKey(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKey
            = SecretKeySpec(generateSK(generateSharedSecret(privateKey, peerPublicKey)), "AES")
}

fun ByteArray.authenticationTag(): ByteArray = copyOfRange(size - Crypto.gcmTagLength, size)
fun ByteArray.encryptedData(): ByteArray = copyOf(size - Crypto.gcmTagLength)
fun ByteArray.toHexArrayString(): String = joinToString(prefix="[", separator=",", postfix="]", transform={ b -> String.format("0x%02x", b) })
