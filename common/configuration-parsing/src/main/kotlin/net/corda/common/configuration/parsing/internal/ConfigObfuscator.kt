package net.corda.common.configuration.parsing.internal

import java.net.NetworkInterface
import java.security.InvalidAlgorithmParameterException
import java.security.SecureRandom
import java.util.*
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Functionality for obfuscating and deobfuscating configuration files.
 */
object ConfigObfuscator {

    class DeobfuscationFailedException(message: String? = null) : IllegalArgumentException(message ?: "Unable to decrypt obfuscated content")

    class DeobfuscationFailedForPathException(val path: String, message: String? = null)
        : IllegalArgumentException(message?.let { "$it for configuration '$path'" } ?: "Unable to decrypt obfuscated content for configuration '$path'")

    /**
     * Obfuscate tagged fields in the provided configuration string.
     */
    fun obfuscateConfiguration(config: String, hardwareAddress: ByteArray? = null, seed: ByteArray? = null, inputDelegate: ((String) -> String)? = null): ObfuscationResult {
        val commandBlobRegex = "^(.*?)<encrypt\\{(.*)\\}>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val aesKey = aesKeyFromHardwareAddress(seed, hardwareAddress ?: getPrimaryHardwareAddress())
        val rawFields = mutableListOf<String>()
        var fieldCount = 0
        val content = commandBlobRegex.replace(config) { match ->
            val fieldName = match.groupValues[1].trim().replace("[:=\"' \t]".toRegex(), "").split(',').last().let {
                if (it.isEmpty()) { "<unknown>" } else { it }
            }
            val value = if (inputDelegate != null) {
                inputDelegate(fieldName)
            } else {
                match.groupValues[2]
            }
            val encryptedValue = CipherText.encrypt(value.toByteArray(), aesKey)
            fieldCount += 1
            rawFields.add("$fieldName: $value")
            "${match.groupValues[1]}<{${encryptedValue.initializationVector.asBase64()}:${encryptedValue.bytes.asBase64()}}>"
        }
        return ObfuscationResult(content = content, fieldCount = fieldCount, rawFields = rawFields)
    }

    /**
     * Decrypt obfuscated fields in the provided configuration string.
     * @throws DeobfuscationFailedException Thrown if unable to decrypt the provided byte array.
     */
    @Throws(DeobfuscationFailedException::class)
    fun deobfuscateConfiguration(config: String, hardwareAddress: ByteArray? = null, seed: ByteArray? = null): DeobfuscationResult {
        val commandBlobRegex = Regex("<\\{([^:]+):([^\\}]+)\\}>", RegexOption.IGNORE_CASE)
        val aesKey = aesKeyFromHardwareAddress(seed, hardwareAddress ?: getPrimaryHardwareAddress())
        var fieldCount = 0
        val content = commandBlobRegex.replace(config) { match ->
            val iv = match.groupValues[1].fromBase64()
            val value = match.groupValues[2].fromBase64()
            val decryptedValue = CipherText(value, iv).decrypt(aesKey)
            fieldCount += 1
            String(decryptedValue)
        }
        return DeobfuscationResult(content = content, fieldCount = fieldCount)
    }

    /**
     * The output produced from obfuscating a configuration string.
     * @property content The obfuscated content.
     * @property fieldCount The number of fields that were obfuscated.
     * @property rawFields The list of fields that have been obfuscated (in their original format).
     */
    class ObfuscationResult(
            val content: String,
            val fieldCount: Int,
            val rawFields: List<String>
    )

    /**
     * The output produced from deobfuscating an obfuscated configuration string.
     * @property content The deobfuscated content.
     * @property fieldCount The number of fields that were deobfuscated.
     */
    class DeobfuscationResult(
            val content: String,
            val fieldCount: Int
    )

    /**
     * Representation of an AES-CBC encrypted byte array.
     * @property bytes The encrypted bytes.
     * @property initializationVector The initialization vector used to encrypt the input.
     */
    private class CipherText(val bytes: ByteArray, val initializationVector: ByteArray) {
        /**
         * Decrypt the cipher text using the provided AES key.
         * @param key The AES key to use for decryption (16 bytes).
         * @throws DeobfuscationFailedException Thrown if unable to decrypt the provided byte array.
         */
        @Throws(DeobfuscationFailedException::class)
        fun decrypt(key: ByteArray): ByteArray {
            require(key.size == 16) { "Invalid key length - must be exactly 16 bytes long" }
            require(initializationVector.size == 16) { "Invalid initialization vector - must be exactly 16 bytes long" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = GCMParameterSpec(16 * 8, initializationVector)
            return cipher.let {
                try {
                    it.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    it.updateAAD(aad)
                    it.doFinal(bytes)
                } catch (e: AEADBadTagException) {
                    throw DeobfuscationFailedException()
                } catch (e: InvalidAlgorithmParameterException) {
                    throw DeobfuscationFailedException()
                }
            }
        }

        companion object {
            // Evidence of no tampering
            private val aad = byteArrayOf(0x00, 0x77, 0x11, 0x66, 0x22, 0x55, 0x33, 0x44)

            /**
             * Encrypt the plain text using the provided AES key.
             * @param plainText The text to encrypt.
             * @param key The AES key to use for decryption (16 bytes).
             */
            fun encrypt(plainText: ByteArray, key: ByteArray): CipherText {
                require(key.size == 16) { "Invalid key length - must be exactly 16 bytes long" }
                val iv = generateInitializationVector()
                val keySpec = SecretKeySpec(key, "AES")
                val ivSpec = GCMParameterSpec(16 * 8, iv)
                val text = Cipher.getInstance("AES/GCM/NoPadding").let {
                    it.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                    it.updateAAD(aad)
                    it.doFinal(plainText)
                }
                return CipherText(text, iv)
            }

            private fun generateInitializationVector() = ByteArray(128 / 8).apply { SecureRandom().nextBytes(this) }
        }
    }

    /**
     * Get the primary hardware address of the machine.
     */
    private fun getPrimaryHardwareAddress(): ByteArray {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.hardwareAddress != null && !it.isLoopback }
                .sortedBy { Interface(it.isVirtual, it.displayName) }
        val mainInterface = interfaces.firstOrNull { "ew".contains(it.displayName[0]) } ?: interfaces.firstOrNull()
        return mainInterface?.hardwareAddress ?: throw Exception("Unable to find machine's hardware address")
    }

    private data class Interface(val isVirtual: Boolean, val displayName: String) : Comparable<Interface> {
        override fun compareTo(other: Interface): Int {
            if (isVirtual != other.isVirtual) {
                return isVirtual.compareTo(other.isVirtual)
            }
            return displayName.compareTo(other.displayName)
        }
    }

    /**
     * Derive an AES key from the provided hardware address.
     */
    private fun aesKeyFromHardwareAddress(seed: ByteArray? = null, hardwareAddress: ByteArray): ByteArray {
        val seedSource = if (seed?.size ?: 0 == 0) { null } else { seed } ?: byteArrayOf('C'.toByte(), 'o'.toByte(), 'r'.toByte(), 'd'.toByte(), 'a'.toByte())
        val expandedSeed = seedSource.repeated().take(16)
        val expandedHardwareAddress = hardwareAddress.repeated().take(16)
        return expandedSeed.zip(expandedHardwareAddress) { a, b -> ((a + b) and 0xff).toByte() }.toList().toByteArray()
    }

    /**
     * Return a repeated sequence of bytes.
     */
    private fun ByteArray.repeated(): Sequence<Byte> = generateSequence { this.asIterable() }.flatten()

    /**
     * Return a base64 encoded version of the provided byte array.
     */
    private fun ByteArray.asBase64(): String = try { Base64.getEncoder().encodeToString(this) } catch (_: Exception) { "" }

    /**
     * Decode a base64 encoded string into a byte array.
     */
    private fun String.fromBase64(): ByteArray = try { Base64.getDecoder().decode(this) } catch (_: Exception) { byteArrayOf() }
}