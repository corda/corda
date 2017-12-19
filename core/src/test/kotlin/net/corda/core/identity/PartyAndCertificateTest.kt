package net.corda.core.identity

import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.internal.cert
import net.corda.core.internal.read
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.save
import net.corda.testing.DEV_CA
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.getTestPartyAndCertificate
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.KeyStore
import kotlin.test.assertFailsWith

class PartyAndCertificateTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `should reject a path with no roles`() {
        val path =  X509CertificateFactory().generateCertPath(DEV_CA.certificate.cert)
        assertFailsWith<IllegalArgumentException> { PartyAndCertificate(path) }
    }

    @Test
    fun `kryo serialisation`() {
        val original = getTestPartyAndCertificate(Party(
                CordaX500Name(organisation = "Test Corp", locality = "Madrid", country = "ES"),
                entropyToKeyPair(BigInteger.valueOf(83)).public))
        val copy = original.serialize().deserialize()
        assertThat(copy).isEqualTo(original).isNotSameAs(original)
        assertThat(copy.certPath).isEqualTo(original.certPath)
        assertThat(copy.certificate).isEqualTo(original.certificate)
    }

    @Test
    fun `jdk serialization`() {
        val identity = getTestPartyAndCertificate(Party(
                CordaX500Name(organisation = "Test Corp", locality = "Madrid", country = "ES"),
                entropyToKeyPair(BigInteger.valueOf(83)).public))
        val original = identity.certificate
        val storePassword = "test"
        val keyStoreFilePath = File.createTempFile("serialization_test", "jks").toPath()
        var keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, storePassword.toCharArray())
        keyStore.setCertificateEntry(identity.name.toString(), original)
        keyStore.save(keyStoreFilePath, storePassword)

        // Load the key store back in again
        keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStoreFilePath.read { keyStore.load(it, storePassword.toCharArray()) }
        val copy = keyStore.getCertificate(identity.name.toString())
        assertThat(copy).isEqualTo(original) // .isNotSameAs(original)
    }
}
