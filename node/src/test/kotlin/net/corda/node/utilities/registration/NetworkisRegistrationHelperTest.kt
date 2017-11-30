package net.corda.node.utilities.registration

import com.nhaarman.mockito_kotlin.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.getX509Certificate
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.ALICE
import net.corda.testing.rigorousMock
import net.corda.testing.testNodeConfiguration
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val X500Name.commonName: String? get() = getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString()

class NetworkRegistrationHelperTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun buildKeyStore() {
        val id = SecureHash.randomSHA256().toString()

        val identities = listOf("CORDA_CLIENT_CA",
                "CORDA_INTERMEDIATE_CA",
                "CORDA_ROOT_CA")
                .map { CordaX500Name(commonName = it, organisation = "R3 Ltd", locality = "London", country = "GB") }
        val certs = identities.stream().map { X509Utilities.createSelfSignedCACertificate(it, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)) }
                .map { it.cert }.toTypedArray()
        val certService = rigorousMock<NetworkRegistrationService>().also {
            doReturn(id).whenever(it).submitRequest(any())
            doReturn(certs).whenever(it).retrieveCertificates(eq(id))
        }

        val config = testNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name)

        config.rootCaCertFile.parent.createDirectories()
        X509Utilities.saveCertificateAsPEMFile(certs.last().toX509CertHolder(), config.rootCaCertFile)

        assertFalse(config.nodeKeystore.exists())
        assertFalse(config.sslKeystore.exists())
        assertFalse(config.trustStoreFile.exists())

        NetworkRegistrationHelper(config, certService).buildKeystore()

        assertTrue(config.nodeKeystore.exists())
        assertTrue(config.sslKeystore.exists())
        assertTrue(config.trustStoreFile.exists())

        val nodeKeystore = loadKeyStore(config.nodeKeystore, config.keyStorePassword)
        val sslKeystore = loadKeyStore(config.sslKeystore, config.keyStorePassword)
        val trustStore = loadKeyStore(config.trustStoreFile, config.trustStorePassword)

        nodeKeystore.run {
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            val certificateChain = getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
            assertEquals(3, certificateChain.size)
            assertEquals(listOf("CORDA_CLIENT_CA", "CORDA_INTERMEDIATE_CA", "CORDA_ROOT_CA"), certificateChain.map { it.toX509CertHolder().subject.commonName })
        }

        sslKeystore.run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            val certificateChain = getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
            assertEquals(4, certificateChain.size)
            assertEquals(listOf(CordaX500Name(organisation = "R3 Ltd", locality = "London", country = "GB").x500Name) + identities.map { it.x500Name },
                    certificateChain.map { it.toX509CertHolder().subject })
            assertEquals(CordaX500Name(organisation = "R3 Ltd", locality = "London", country = "GB").x500Principal,
                    getX509Certificate(X509Utilities.CORDA_CLIENT_TLS).subjectX500Principal)
        }

        trustStore.run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertTrue(containsAlias(X509Utilities.CORDA_ROOT_CA))
        }
    }
}
