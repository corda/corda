package net.corda.node.services.persistence

import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.Party
import net.corda.core.internal.copyBytes
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.EncryptionService
import net.corda.nodeapi.internal.crypto.AesEncryption
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.hibernate.annotations.Type
import java.nio.ByteBuffer
import java.security.Key
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * [EncryptionService] which uses AES keys stored in the node database. A random key is chosen for encryption, and the resultant ciphertext
 * encodes the key used so that it can be decrypted without needing further information.
 *
 * **Storing encryption keys in a database is not secure, and so only use this service if the data being encrypted is also stored
 * unencrypted in the same database.**
 *
 * To obfuscate the keys, they are stored wrapped using another AES key (called the wrapping key or key-encryption-key) derived from the
 * node's legal identity. This is not a security measure; it's only meant to reduce the impact of accidental leakage.
 */
// TODO Add support for key expiry
class AesDbEncryptionService(private val database: CordaPersistence) : EncryptionService, SingletonSerializeAsToken() {
    companion object {
        private const val INITIAL_KEY_COUNT = 10
        private const val UUID_BYTES = 16
    }

    private val aesKeys = ArrayList<Pair<UUID, SecretKey>>()

    fun start(ourIdentity: Party) {
        database.transaction {
            val criteria = session.criteriaBuilder.createQuery(EncryptionKeyRecord::class.java)
            criteria.select(criteria.from(EncryptionKeyRecord::class.java))
            val dbKeyRecords = session.createQuery(criteria).resultList
            val keyWrapper = Cipher.getInstance("AESWrap")
            if (dbKeyRecords.isEmpty()) {
                repeat(INITIAL_KEY_COUNT) {
                    val keyId = UUID.randomUUID()
                    val aesKey = AesEncryption.randomKey()
                    aesKeys += Pair(keyId, aesKey)
                    val wrappedKey = with(keyWrapper) {
                        init(Cipher.WRAP_MODE, createKEK(ourIdentity, keyId))
                        wrap(aesKey)
                    }
                    session.save(EncryptionKeyRecord(keyId = keyId, keyMaterial = wrappedKey))
                }
            } else {
                for (dbKeyRecord in dbKeyRecords) {
                    val aesKey = with(keyWrapper) {
                        init(Cipher.UNWRAP_MODE, createKEK(ourIdentity, dbKeyRecord.keyId))
                        unwrap(dbKeyRecord.keyMaterial, "AES", Cipher.SECRET_KEY) as SecretKey
                    }
                    aesKeys += Pair(dbKeyRecord.keyId, aesKey)
                }
            }
        }
    }

    override fun encrypt(plaintext: ByteArray, additionalData: ByteArray?): ByteArray {
        val (keyId, aesKey) = aesKeys[newSecureRandom().nextInt(aesKeys.size)]
        val ciphertext = AesEncryption.encrypt(aesKey, plaintext, additionalData)
        val buffer = ByteBuffer.allocate(1 + UUID_BYTES + Integer.BYTES + (additionalData?.size ?: 0) + ciphertext.size)
        buffer.put(1)  // Version tag
        // Prepend the key ID to the returned ciphertext. It's OK that this is not included in the authenticated additional data because
        // changing this value will lead to either an non-existent key or an another key which will not be able decrypt the ciphertext.
        buffer.putUUID(keyId)
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
        val version = buffer.get().toInt()
        require(version == 1)
        val keyId = buffer.getUUID()
        val aesKey = requireNotNull(aesKeys.find { it.first == keyId }?.second) { "Unable to decrypt" }
        val additionalData = buffer.getAdditionaData()
        val plaintext = AesEncryption.decrypt(aesKey, buffer.copyBytes(), additionalData)
        // Only now is the additional data authenticated
        return EncryptionService.PlaintextAndAAD(plaintext, additionalData)
    }

    override fun extractUnauthenticatedAdditionalData(ciphertext: ByteArray): ByteArray? {
        val buffer = ByteBuffer.wrap(ciphertext)
        buffer.position(1 + UUID_BYTES)
        return buffer.getAdditionaData()
    }

    private fun ByteBuffer.getAdditionaData(): ByteArray? {
        val additionalDataSize = getInt()
        return if (additionalDataSize > 0) ByteArray(additionalDataSize).also { get(it) } else null
    }

    private fun UUID.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(UUID_BYTES)
        buffer.putUUID(this)
        return buffer.array()
    }

    /**
     * Derive the key-encryption-key (KEK) from the the node's identity and the persisted key's ID.
     */
    private fun createKEK(ourIdentity: Party, keyId: UUID): Key {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ourIdentity.name.x500Principal.encoded)
        digest.update(keyId.toByteArray())
        return SecretKeySpec(digest.digest(), 0, AesEncryption.KEY_SIZE_BYTES, "AES")
    }


    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}aes_encryption_keys")
    class EncryptionKeyRecord(
            @Id
            @Type(type = "uuid-char")
            @Column(name = "key_id", nullable = false)
            val keyId: UUID,

            @Column(name = "key_material", nullable = false)
            val keyMaterial: ByteArray
    )
}

internal fun ByteBuffer.putUUID(uuid: UUID) {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
}

internal fun ByteBuffer.getUUID(): UUID {
    val mostSigBits = getLong()
    val leastSigBits = getLong()
    return UUID(mostSigBits, leastSigBits)
}
