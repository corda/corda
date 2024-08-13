package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.secureRandomBytes
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesEncryption {
    const val KEY_SIZE_BYTES = 16
    internal const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BYTES = 16
    private const val TAG_SIZE_BITS = TAG_SIZE_BYTES * 8

    /**
     * Generates a random 128-bit AES key.
     */
    fun randomKey(): SecretKey {
        return SecretKeySpec(secureRandomBytes(KEY_SIZE_BYTES), "AES")
    }

    /**
     * Encrypt the given [plaintext] with AES using the given [aesKey].
     *
     * An optional public [additionalData] bytes can also be provided which will be authenticated alongside the ciphertext but not encrypted.
     * This may be metadata for example. The same authenticated data bytes must be provided to [decrypt] to be able to decrypt the
     * ciphertext. Typically these bytes are serialised alongside the ciphertext. Since it's authenticated in the ciphertext, it cannot be
     * modified undetected.
     */
    fun encrypt(aesKey: SecretKey, plaintext: ByteArray, additionalData: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = secureRandomBytes(IV_SIZE_BYTES)  // Never use the same IV with the same key!
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val buffer = ByteBuffer.allocate(IV_SIZE_BYTES + plaintext.size + TAG_SIZE_BYTES)
        buffer.put(iv)
        if (additionalData != null) {
            cipher.updateAAD(additionalData)
        }
        cipher.doFinal(ByteBuffer.wrap(plaintext), buffer)
        return buffer.array()
    }

    fun encrypt(aesKey: ByteArray, plaintext: ByteArray, additionalData: ByteArray? = null): ByteArray {
        return encrypt(SecretKeySpec(aesKey, "AES"), plaintext, additionalData)
    }

    /**
     * Decrypt ciphertext that was encrypted with the same key using [encrypt].
     *
     * If additional data was used for the encryption then it must also be provided. If doesn't match then the decryption will fail.
     */
    fun decrypt(aesKey: SecretKey, ciphertext: ByteArray, additionalData: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE_BITS, ciphertext, 0, IV_SIZE_BYTES))
        if (additionalData != null) {
            cipher.updateAAD(additionalData)
        }
        return cipher.doFinal(ciphertext, IV_SIZE_BYTES, ciphertext.size - IV_SIZE_BYTES)
    }

    fun decrypt(aesKey: ByteArray, ciphertext: ByteArray, additionalData: ByteArray? = null): ByteArray {
        return decrypt(SecretKeySpec(aesKey, "AES"), ciphertext, additionalData)
    }
}
