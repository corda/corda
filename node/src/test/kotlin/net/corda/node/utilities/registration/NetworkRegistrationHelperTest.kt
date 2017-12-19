package net.corda.node.utilities.registration

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.createDirectories
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.ALICE_NAME
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkRegistrationHelperTest {
    private val fs = Jimfs.newFileSystem(unix())
    private val requestId = SecureHash.randomSHA256().toString()
    private val nodeLegalName = ALICE_NAME
    private val intermediateCaName = CordaX500Name("CORDA_INTERMEDIATE_CA", "R3 Ltd", "London", "GB")
    private val rootCaName = CordaX500Name("CORDA_ROOT_CA", "R3 Ltd", "London", "GB")
    private val nodeCaCert = createCaCert(nodeLegalName)
    private val intermediateCaCert = createCaCert(intermediateCaName)
    private val rootCaCert = createCaCert(rootCaName)

    private lateinit var config: NodeConfiguration

    @Before
    fun init() {
        val baseDirectory = fs.getPath("/baseDir").createDirectories()
        abstract class AbstractNodeConfiguration : NodeConfiguration
        config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(nodeLegalName).whenever(it).myLegalName
            doReturn("").whenever(it).emailAddress
        }
    }

    @After
    fun cleanUp() {
        fs.close()
    }

    @Test
    fun `successful registration`() {
        assertThat(config.nodeKeystore).doesNotExist()
        assertThat(config.sslKeystore).doesNotExist()
        assertThat(config.trustStoreFile).doesNotExist()

        saveTrustStoreWithRootCa(rootCaCert)

        createRegistrationHelper().buildKeystore()

        val nodeKeystore = loadKeyStore(config.nodeKeystore, config.keyStorePassword)
        val sslKeystore = loadKeyStore(config.sslKeystore, config.keyStorePassword)
        val trustStore = loadKeyStore(config.trustStoreFile, config.trustStorePassword)

        nodeKeystore.run {
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            val nodeCaCertChain = getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
            assertThat(nodeCaCertChain).containsExactly(nodeCaCert, intermediateCaCert, rootCaCert)
        }

        sslKeystore.run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            val nodeTlsCertChain = getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
            assertThat(nodeTlsCertChain).hasSize(4)
            // The TLS cert has the same subject as the node CA cert
            assertThat(CordaX500Name.build((nodeTlsCertChain[0] as X509Certificate).subjectX500Principal)).isEqualTo(nodeLegalName)
            assertThat(nodeTlsCertChain.drop(1)).containsExactly(nodeCaCert, intermediateCaCert, rootCaCert)
        }

        trustStore.run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertTrue(containsAlias(X509Utilities.CORDA_ROOT_CA))
            val trustStoreRootCaCert = getCertificate(X509Utilities.CORDA_ROOT_CA)
            assertThat(trustStoreRootCaCert).isEqualTo(rootCaCert)
        }
    }

    @Test
    fun `missing truststore`() {
        assertThatThrownBy {
            createRegistrationHelper()
        }.hasMessageContaining("This file must contain the root CA cert of your compatibility zone. Please contact your CZ operator.")
    }

    @Test
    fun `wrong root cert in truststore`() {
        saveTrustStoreWithRootCa(createCaCert(CordaX500Name("Foo", "MU", "GB")))
        val registrationHelper = createRegistrationHelper()
        assertThatThrownBy {
            registrationHelper.buildKeystore()
        }.isInstanceOf(WrongRootCertException::class.java)
    }

    private fun createRegistrationHelper(): NetworkRegistrationHelper {
        val certService = rigorousMock<NetworkRegistrationService>().also {
            doReturn(requestId).whenever(it).submitRequest(any())
            doReturn(arrayOf<Certificate>(nodeCaCert, intermediateCaCert, rootCaCert)).whenever(it).retrieveCertificates(eq(requestId))
        }
        return NetworkRegistrationHelper(config, certService)
    }

    private fun saveTrustStoreWithRootCa(rootCa: X509Certificate) {
        config.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCa)
            it.save(config.trustStoreFile, config.trustStorePassword)
        }
    }

    private fun createCaCert(name: CordaX500Name): X509Certificate {
        return X509Utilities.createSelfSignedCACertificate(name, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)).cert
    }
}
