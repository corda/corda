package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.node.services.EncryptionService
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.time.Instant

@Suppress("TooGenericExceptionCaught")
@CordaSerializable
data class HashedDistributionList(
        val senderStatesToRecord: StatesToRecord,
        val peerHashToStatesToRecord: Map<SecureHash, StatesToRecord>,
        val publicHeader: PublicHeader
) {
    /**
     * Encrypt this hashed distribution list using the given [EncryptionService]. The [publicHeader] is not encrypted but is instead
     * authenticated so that it is tamperproof.
     *
     * The same [EncryptionService] instance needs to be used with [decrypt] for decryption.
     */
    fun encrypt(encryptionService: EncryptionService): ByteArray {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)
        out.writeByte(senderStatesToRecord.ordinal)
        out.writeInt(peerHashToStatesToRecord.size)
        for (entry in peerHashToStatesToRecord) {
            entry.key.writeTo(out)
            out.writeByte(entry.value.ordinal)
        }
        return encryptionService.encrypt(baos.toByteArray(), publicHeader.serialise())
    }


    @CordaSerializable
    data class PublicHeader(
        val senderRecordedTimestamp: Instant
    ) {
        fun serialise(): ByteArray {
            val buffer = ByteBuffer.allocate(1 + java.lang.Long.BYTES)
            buffer.put(VERSION_TAG.toByte())
            buffer.putLong(senderRecordedTimestamp.toEpochMilli())
            return buffer.array()
        }

        companion object {
            /**
             * Deserialise a [PublicHeader] from the given [encryptedBytes]. The bytes is expected is to be a valid encrypted blob that can
             * be decrypted by [HashedDistributionList.decrypt] using the same [EncryptionService].
             *
             * Because this method does not actually decrypt the bytes, the header returned is not authenticated and any modifications to it
             * will not be detected. That can only be done by the encrypting party with [HashedDistributionList.decrypt].
             */
            fun unauthenticatedDeserialise(encryptedBytes: ByteArray, encryptionService: EncryptionService): PublicHeader {
                val additionalData = encryptionService.extractUnauthenticatedAdditionalData(encryptedBytes)
                requireNotNull(additionalData) { "Missing additional data field" }
                return deserialise(additionalData!!)
            }

            fun deserialise(bytes: ByteArray): PublicHeader {
                val buffer = ByteBuffer.wrap(bytes)
                try {
                    val version = buffer.get().toInt()
                    require(version == VERSION_TAG) { "Unknown distribution list format $version" }
                    val senderRecordedTimestamp = Instant.ofEpochMilli(buffer.getLong())
                    return PublicHeader(senderRecordedTimestamp)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Corrupt or not a distribution list header", e)
                }
            }
        }
    }

    companion object {
        // The version tag is serialised in the header, even though it is separate from the encrypted main body of the distribution list.
        // This is because the header and the dist list are cryptographically coupled and we want to avoid declaring the version field twice.
        private const val VERSION_TAG = 1
        private val statesToRecordValues = StatesToRecord.values()  // Cache the enum values since .values() returns a new array each time.

        /**
         * Decrypt a [HashedDistributionList] from the given [encryptedBytes] using the same [EncryptionService] that was used in [encrypt].
         */
        fun decrypt(encryptedBytes: ByteArray, encryptionService: EncryptionService): HashedDistributionList {
            val (plaintext, authenticatedAdditionalData) = encryptionService.decrypt(encryptedBytes)
            requireNotNull(authenticatedAdditionalData) { "Missing authenticated header" }
            val publicHeader = PublicHeader.deserialise(authenticatedAdditionalData!!)
            val input = DataInputStream(plaintext.inputStream())
            try {
                val senderStatesToRecord = statesToRecordValues[input.readByte().toInt()]
                val numPeerHashToStatesToRecords = input.readInt()
                val peerHashToStatesToRecord = mutableMapOf<SecureHash, StatesToRecord>()
                repeat(numPeerHashToStatesToRecords) {
                    val secureHashBytes = ByteArray(32)
                    input.readFully(secureHashBytes)
                    peerHashToStatesToRecord[SecureHash.createSHA256(secureHashBytes)] = statesToRecordValues[input.readByte().toInt()]
                }
                return HashedDistributionList(senderStatesToRecord, peerHashToStatesToRecord, publicHeader)
            } catch (e: Exception) {
                throw IllegalArgumentException("Corrupt or not a distribution list", e)
            }
        }
    }
}
