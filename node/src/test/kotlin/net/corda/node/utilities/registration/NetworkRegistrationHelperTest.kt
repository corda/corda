package net.corda.node.utilities.registration

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.getX509Certificate
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.ALICE_NAME
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.cert.Certificate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkRegistrationHelperTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val requestId = SecureHash.randomSHA256().toString()
    private lateinit var config: NodeConfiguration

    private val identities = listOf("CORDA_CLIENT_CA",
            "CORDA_INTERMEDIATE_CA",
            "CORDA_ROOT_CA")
            .map { CordaX500Name(commonName = it, organisation = "R3 Ltd", locality = "London", country = "GB") }
    private val certs = identities.map { X509Utilities.createSelfSignedCACertificate(it, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)) }
            .map { it.cert }.toTypedArray()

    private val certService = mockRegistrationResponse(*certs)

    @Before
    fun init() {
        abstract class AbstractNodeConfiguration : NodeConfiguration
        config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("").whenever(it).emailAddress
        }
    }

    @Test
    fun `successful registration`() {
        assertFalse(config.nodeKeystore.exists())
        assertFalse(config.sslKeystore.exists())
        config.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, certs.last())
            it.save(config.trustStoreFile, config.trustStorePassword)
        }

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

    @Test
    fun `missing truststore`() {
        assertThatThrownBy {
            NetworkRegistrationHelper(config, certService).buildKeystore()
        }.hasMessageContaining("This file must contain the root CA cert of your compatibility zone. Please contact your CZ operator.")
                .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `wrong root cert in truststore`() {
        val someCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name("Foo", "MU", "GB"), Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)).cert
        config.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, someCert)
            it.save(config.trustStoreFile, config.trustStorePassword)
        }
        assertThatThrownBy {
            NetworkRegistrationHelper(config, certService).buildKeystore()
        }.isInstanceOf(WrongRootCertException::class.java)
    }

    private fun mockRegistrationResponse(vararg response: Certificate): NetworkRegistrationService {
        return rigorousMock<NetworkRegistrationService>().also {
            doReturn(requestId).whenever(it).submitRequest(any())
            doReturn(response).whenever(it).retrieveCertificates(eq(requestId))
        }
    }
}
