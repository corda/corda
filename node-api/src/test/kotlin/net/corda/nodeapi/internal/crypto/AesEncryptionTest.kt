package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.secureRandomBytes
import net.corda.nodeapi.internal.crypto.AesEncryption.IV_SIZE_BYTES
import net.corda.nodeapi.internal.crypto.AesEncryption.KEY_SIZE_BYTES
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.security.GeneralSecurityException

class AesEncryptionTest {
    private val aesKey = secureRandomBytes(KEY_SIZE_BYTES)
    private val plaintext = secureRandomBytes(257)  // Intentionally not a power of 2

    @Test(timeout = 300_000)
    fun `ciphertext can be decrypted using the same key`() {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext)
        assertThat(String(ciphertext)).doesNotContain(String(plaintext))
        val decrypted = AesEncryption.decrypt(aesKey, ciphertext)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test(timeout = 300_000)
    fun `ciphertext with authenticated data can be decrypted using the same key`() {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext, "Extra public data".toByteArray())
        assertThat(String(ciphertext)).doesNotContain(String(plaintext))
        val decrypted = AesEncryption.decrypt(aesKey, ciphertext, "Extra public data".toByteArray())
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test(timeout = 300_000)
    fun `ciphertext cannot be decrypted with different authenticated data`() {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext, "Extra public data".toByteArray())
        assertThat(String(ciphertext)).doesNotContain(String(plaintext))
        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            AesEncryption.decrypt(aesKey, ciphertext, "Different public data".toByteArray())
        }
    }

    @Test(timeout = 300_000)
    fun `ciphertext cannot be decrypted with different key`() {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext)
        for (index in aesKey.indices) {
            aesKey[index]--
            assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
                AesEncryption.decrypt(aesKey, ciphertext)
            }
            aesKey[index]++
        }
    }

    @Test(timeout = 300_000)
    fun `corrupted ciphertext cannot be decrypted`() {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext)
        for (index in ciphertext.indices) {
            ciphertext[index]--
            assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
                AesEncryption.decrypt(aesKey, ciphertext)
            }
            ciphertext[index]++
        }
    }

    @Test(timeout = 300_000)
    fun `encrypting same plainttext twice with same key does not produce same ciphertext`() {
        val first = AesEncryption.encrypt(aesKey, plaintext)
        val second = AesEncryption.encrypt(aesKey, plaintext)
        // The IV should be different
        assertThat(first.take(IV_SIZE_BYTES)).isNotEqualTo(second.take(IV_SIZE_BYTES))
        // Which should cause the encrypted bytes to be different as well
        assertThat(first.drop(IV_SIZE_BYTES)).isNotEqualTo(second.drop(IV_SIZE_BYTES))
    }
}
