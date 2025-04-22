package net.corda.testing.node.internal

import net.corda.core.internal.copyBytes
import net.corda.node.services.EncryptionService
import net.corda.nodeapi.internal.crypto.AesEncryption
import java.nio.ByteBuffer
import javax.crypto.SecretKey

class MockEncryptionService(private val aesKey: SecretKey = AesEncryption.randomKey()) : EncryptionService {
    override fun encrypt(plaintext: ByteArray, additionalData: ByteArray?): ByteArray {
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext, additionalData)
        val buffer = ByteBuffer.allocate(Integer.BYTES + (additionalData?.size ?: 0) + ciphertext.size)
        if (additionalData != null) {
            buffer.putInt(additionalData.size)
            buffer.put(additionalData)
        } else {
            buffer.putInt(0)
        }
        buffer.put(ciphertext)
        return buffer.array()
    }

    override fun decrypt(ciphertext: ByteArray): EncryptionService.PlaintextAndAAD {
        val buffer = ByteBuffer.wrap(ciphertext)
        val additionalData = buffer.getAdditionaData()
        val plaintext = AesEncryption.decrypt(aesKey, buffer.copyBytes(), additionalData)
        // Only now is the additional data authenticated
        return EncryptionService.PlaintextAndAAD(plaintext, additionalData)
    }

    override fun extractUnauthenticatedAdditionalData(ciphertext: ByteArray): ByteArray? {
        return ByteBuffer.wrap(ciphertext).getAdditionaData()
    }

    private fun ByteBuffer.getAdditionaData(): ByteArray? {
        val additionalDataSize = getInt()
        return if (additionalDataSize > 0) ByteArray(additionalDataSize).also { get(it) } else null
    }
}
