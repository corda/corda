package net.corda.node.services.persistence

import net.corda.node.services.persistence.AesDbEncryptionService.EncryptionKeyRecord
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.UUID

class AesDbEncryptionServiceTest {
    private val identity = TestIdentity.fresh("me").party
    private lateinit var database: CordaPersistence
    private lateinit var encryptionService: AesDbEncryptionService

    @Before
    fun setUp() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps, DatabaseConfig(), { null }, { null })
        encryptionService = AesDbEncryptionService(database)
        encryptionService.start(identity)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test(timeout = 300_000)
    fun `same instance can decrypt ciphertext`() {
        val ciphertext = encryptionService.encrypt("Hello World".toByteArray())
        val (plaintext, authenticatedData) = encryptionService.decrypt(ciphertext)
        assertThat(String(plaintext)).isEqualTo("Hello World")
        assertThat(authenticatedData).isNull()
    }

    @Test(timeout = 300_000)
    fun `encypting twice produces different ciphertext`() {
        val plaintext = "Hello".toByteArray()
        assertThat(encryptionService.encrypt(plaintext)).isNotEqualTo(encryptionService.encrypt(plaintext))
    }

    @Test(timeout = 300_000)
    fun `ciphertext can be decrypted after restart`() {
        val ciphertext = encryptionService.encrypt("Hello World".toByteArray())
        encryptionService = AesDbEncryptionService(database)
        encryptionService.start(identity)
        val plaintext = encryptionService.decrypt(ciphertext).plaintext
        assertThat(String(plaintext)).isEqualTo("Hello World")
    }

    @Test(timeout = 300_000)
    fun `encrypting with authenticated data`() {
        val ciphertext = encryptionService.encrypt("Hello World".toByteArray(), "Additional data".toByteArray())
        val (plaintext, authenticatedData) = encryptionService.decrypt(ciphertext)
        assertThat(String(plaintext)).isEqualTo("Hello World")
        assertThat(authenticatedData?.let { String(it) }).isEqualTo("Additional data")
    }

    @Test(timeout = 300_000)
    fun extractUnauthenticatedAdditionalData() {
        val ciphertext = encryptionService.encrypt("Hello World".toByteArray(), "Additional data".toByteArray())
        val additionalData = encryptionService.extractUnauthenticatedAdditionalData(ciphertext)
        assertThat(additionalData?.let { String(it) }).isEqualTo("Additional data")
    }

    @Test(timeout = 300_000)
    fun `ciphertext cannot be decrypted if the authenticated data is modified`() {
        val ciphertext = ByteBuffer.wrap(encryptionService.encrypt("Hello World".toByteArray(), "1234".toByteArray()))

        ciphertext.position(21)
        ciphertext.put("4321".toByteArray())  // Use same length for the modified AAD

        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            encryptionService.decrypt(ciphertext.array())
        }
    }

    @Test(timeout = 300_000)
    fun `ciphertext cannot be decrypted if the key used is deleted`() {
        val ciphertext = encryptionService.encrypt("Hello World".toByteArray())
        val keyId = ByteBuffer.wrap(ciphertext).getKeyId()
        val deletedCount = database.transaction {
            session.createQuery("DELETE FROM ${EncryptionKeyRecord::class.java.name} k WHERE k.keyId = :keyId")
                    .setParameter("keyId", keyId)
                    .executeUpdate()
        }
        assertThat(deletedCount).isEqualTo(1)

        encryptionService = AesDbEncryptionService(database)
        encryptionService.start(identity)
        assertThatIllegalArgumentException().isThrownBy {
            encryptionService.decrypt(ciphertext)
        }
    }

    @Test(timeout = 300_000)
    fun `ciphertext cannot be decrypted if forced to use a different key`() {
        val ciphertext = ByteBuffer.wrap(encryptionService.encrypt("Hello World".toByteArray()))
        val keyId = ciphertext.getKeyId()
        val anotherKeyId = database.transaction {
            session.createQuery("SELECT keyId FROM ${EncryptionKeyRecord::class.java.name} k WHERE k.keyId != :keyId", UUID::class.java)
                    .setParameter("keyId", keyId)
                    .setMaxResults(1)
                    .singleResult
        }

        ciphertext.putKeyId(anotherKeyId)

        encryptionService = AesDbEncryptionService(database)
        encryptionService.start(identity)
        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            encryptionService.decrypt(ciphertext.array())
        }
    }

    private fun ByteBuffer.getKeyId(): UUID {
        position(1)
        return getUUID()
    }

    private fun ByteBuffer.putKeyId(keyId: UUID) {
        position(1)
        putUUID(keyId)
    }
}
