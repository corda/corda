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
import net.corda.core.internal.createDirectories
import net.corda.core.internal.x500Name
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.ALICE_NAME
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.cert.CertPathValidatorException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkRegistrationHelperTest {
    private val fs = Jimfs.newFileSystem(unix())
    private val requestId = SecureHash.randomSHA256().toString()
    private val nodeLegalName = ALICE_NAME

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

        val nodeCaCertPath = createNodeCaCertPath()

        saveTrustStoreWithRootCa(nodeCaCertPath.last())
        createRegistrationHelper(nodeCaCertPath).buildKeystore()

        val nodeKeystore = loadKeyStore(config.nodeKeystore, config.keyStorePassword)
        val sslKeystore = loadKeyStore(config.sslKeystore, config.keyStorePassword)
        val trustStore = loadKeyStore(config.trustStoreFile, config.trustStorePassword)

        nodeKeystore.run {
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            assertThat(getCertificateChain(X509Utilities.CORDA_CLIENT_CA)).containsExactly(*nodeCaCertPath)
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
            assertThat(nodeTlsCertChain.drop(1)).containsExactly(*nodeCaCertPath)
        }

        trustStore.run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertTrue(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertThat(getCertificate(X509Utilities.CORDA_ROOT_CA)).isEqualTo(nodeCaCertPath.last())
        }
    }

    @Test
    fun `missing truststore`() {
        val nodeCaCertPath = createNodeCaCertPath()
        assertThatThrownBy {
            createRegistrationHelper(nodeCaCertPath)
        }.hasMessageContaining("This file must contain the root CA cert of your compatibility zone. Please contact your CZ operator.")
    }

    @Test
    fun `node CA with incorrect cert role`() {
        val nodeCaCertPath = createNodeCaCertPath(type = CertificateType.TLS)
        saveTrustStoreWithRootCa(nodeCaCertPath.last())
        val registrationHelper = createRegistrationHelper(nodeCaCertPath)
        assertThatExceptionOfType(CertificateRequestException::class.java)
                .isThrownBy { registrationHelper.buildKeystore() }
                .withMessageContaining(CertificateType.TLS.toString())
    }

    @Test
    fun `node CA with incorrect subject`() {
        val invalidName = CordaX500Name("Foo", "MU", "GB")
        val nodeCaCertPath = createNodeCaCertPath(legalName = invalidName)
        saveTrustStoreWithRootCa(nodeCaCertPath.last())
        val registrationHelper = createRegistrationHelper(nodeCaCertPath)
        assertThatExceptionOfType(CertificateRequestException::class.java)
                .isThrownBy { registrationHelper.buildKeystore() }
                .withMessageContaining(invalidName.toString())
    }

    @Test
    fun `wrong root cert in truststore`() {
        val wrongRootCert = X509Utilities.createSelfSignedCACertificate(
                X500Principal("O=Foo,L=MU,C=GB"),
                Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        saveTrustStoreWithRootCa(wrongRootCert)
        val registrationHelper = createRegistrationHelper(createNodeCaCertPath())
        assertThatThrownBy {
            registrationHelper.buildKeystore()
        }.isInstanceOf(CertPathValidatorException::class.java)
    }

    private fun createNodeCaCertPath(type: CertificateType = CertificateType.NODE_CA,
                                     legalName: CordaX500Name = nodeLegalName): Array<X509Certificate> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.x500Name))), arrayOf())
        val nodeCaCert = X509Utilities.createCertificate(
                type,
                intermediateCa.certificate,
                intermediateCa.keyPair,
                legalName.x500Principal,
                keyPair.public,
                nameConstraints = nameConstraints)
        return arrayOf(nodeCaCert, intermediateCa.certificate, rootCa.certificate)
    }

    private fun createRegistrationHelper(response: Array<X509Certificate>): NetworkRegistrationHelper {
        val certService = rigorousMock<NetworkRegistrationService>().also {
            doReturn(requestId).whenever(it).submitRequest(any())
            doReturn(response).whenever(it).retrieveCertificates(eq(requestId))
        }
        return NetworkRegistrationHelper(config, certService)
    }

    private fun saveTrustStoreWithRootCa(rootCert: X509Certificate) {
        config.certificatesDirectory.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
            it.save(config.trustStoreFile, config.trustStorePassword)
        }
    }
}
