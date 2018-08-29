package net.corda.node.utilities.registration

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.toX500Name
import net.corda.core.utilities.seconds
import net.corda.node.NodeRegistrationOption
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.stubs.CertificateStoreStubs
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.*
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.security.cert.CertPathValidatorException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFalse

class NetworkRegistrationHelperTest {
    private val fs = Jimfs.newFileSystem(unix())
    private val nodeLegalName = ALICE_NAME

    private lateinit var config: NodeConfiguration
    private val networkRootTrustStoreFileName = "network-root-truststore.jks"
    private val networkRootTrustStorePassword = "network-root-truststore-password"

    @Before
    fun init() {
        val baseDirectory = fs.getPath("/baseDir").createDirectories()

        abstract class AbstractNodeConfiguration : NodeConfiguration
        config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(CertificateStoreStubs.P2P.withBaseDirectory(baseDirectory)).whenever(it).p2pSslConfiguration
            doReturn(CertificateStoreStubs.Signing.withBaseDirectory(baseDirectory)).whenever(it).signingCertificateStore
            doReturn(nodeLegalName).whenever(it).myLegalName
            doReturn("").whenever(it).emailAddress
            doReturn(null).whenever(it).tlsCertCrlDistPoint
            doReturn(null).whenever(it).tlsCertCrlIssuer
            doReturn(true).whenever(it).crlCheckSoftFail
            doReturn(baseDirectory / "certificates").whenever(it).certificatesDirectory
        }
    }

    @After
    fun cleanUp() {
        fs.close()
    }

    @Test
    fun `successful registration`() {
        assertThat(config.signingCertificateStore.getOptional()).isNull()
        assertThat(config.p2pSslConfiguration.keyStore.getOptional()).isNull()
        assertThat(config.p2pSslConfiguration.trustStore.getOptional()).isNull()

        val rootAndIntermediateCA = createDevIntermediateCaCertPath().also { saveNetworkTrustStore(it.first.certificate) }

        createRegistrationHelper(rootAndIntermediateCA = rootAndIntermediateCA).buildKeystore()

        val nodeKeystore = config.signingCertificateStore.get()
        val sslKeystore = config.p2pSslConfiguration.keyStore.get()
        val trustStore = config.p2pSslConfiguration.trustStore.get()

        nodeKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_TLS))
            assertThat(CertRole.extract(getCertificate(X509Utilities.CORDA_CLIENT_CA))).isEqualTo(CertRole.NODE_CA)
        }

        sslKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            val nodeTlsCertChain = getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
            assertThat(nodeTlsCertChain).hasSize(4)
            // The TLS cert has the same subject as the node CA cert
            assertThat(CordaX500Name.build(nodeTlsCertChain[0].subjectX500Principal)).isEqualTo(nodeLegalName)
            assertThat(CertRole.extract(nodeTlsCertChain.first())).isEqualTo(CertRole.TLS)
        }

        trustStore.run {
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertThat(getCertificate(X509Utilities.CORDA_ROOT_CA)).isEqualTo(rootAndIntermediateCA.first.certificate)
        }
    }

    @Test
    fun `missing truststore`() {
        val nodeCaCertPath = createNodeCaCertPath()
        assertThatThrownBy {
            createFixedResponseRegistrationHelper(nodeCaCertPath)
        }.hasMessageContaining("This file must contain the root CA cert of your compatibility zone. Please contact your CZ operator.")
    }

    @Test
    fun `node CA with incorrect cert role`() {
        val nodeCaCertPath = createNodeCaCertPath(type = CertificateType.TLS)
        saveNetworkTrustStore(nodeCaCertPath.last())
        val registrationHelper = createFixedResponseRegistrationHelper(nodeCaCertPath)
        assertThatExceptionOfType(CertificateRequestException::class.java)
                .isThrownBy { registrationHelper.buildKeystore() }
                .withMessageContaining(CertificateType.TLS.toString())
    }

    @Test
    fun `node CA with incorrect subject`() {
        val invalidName = CordaX500Name("Foo", "MU", "GB")
        val nodeCaCertPath = createNodeCaCertPath(legalName = invalidName)
        saveNetworkTrustStore(nodeCaCertPath.last())
        val registrationHelper = createFixedResponseRegistrationHelper(nodeCaCertPath)
        assertThatExceptionOfType(CertificateRequestException::class.java)
                .isThrownBy { registrationHelper.buildKeystore() }
                .withMessageContaining(invalidName.toString())
    }

    @Test
    fun `wrong root cert in truststore`() {
        val wrongRootCert = X509Utilities.createSelfSignedCACertificate(
                X500Principal("O=Foo,L=MU,C=GB"),
                Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        saveNetworkTrustStore(wrongRootCert)

        val registrationHelper = createRegistrationHelper()
        assertThatThrownBy {
            registrationHelper.buildKeystore()
        }.isInstanceOf(CertPathValidatorException::class.java)
    }

    @Test
    fun `create service identity cert`() {
        assertThat(config.signingCertificateStore.getOptional()).isNull()
        assertThat(config.p2pSslConfiguration.keyStore.getOptional()).isNull()
        assertThat(config.p2pSslConfiguration.trustStore.getOptional()).isNull()

        val rootAndIntermediateCA = createDevIntermediateCaCertPath().also { saveNetworkTrustStore(it.first.certificate) }

        createRegistrationHelper(CertRole.SERVICE_IDENTITY, rootAndIntermediateCA).buildKeystore()

        val nodeKeystore = config.signingCertificateStore.get()

        assertThat(config.p2pSslConfiguration.keyStore.getOptional()).isNull()
        assertThat(config.p2pSslConfiguration.trustStore.getOptional()).isNull()

        val serviceIdentityAlias = "${DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key"

        nodeKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_TLS))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertThat(CertRole.extract(getCertificate(serviceIdentityAlias))).isEqualTo(CertRole.SERVICE_IDENTITY)
        }
    }

    private fun createNodeCaCertPath(type: CertificateType = CertificateType.NODE_CA,
                                     legalName: CordaX500Name = nodeLegalName,
                                     publicKey: PublicKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME).public,
                                     rootAndIntermediateCA: Pair<CertificateAndKeyPair, CertificateAndKeyPair> = createDevIntermediateCaCertPath()): List<X509Certificate> {
        val (rootCa, intermediateCa) = rootAndIntermediateCA
        val nameConstraints = if (type == CertificateType.NODE_CA) {
            NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.toX500Name()))), arrayOf())
        } else {
            null
        }
        val nodeCaCert = X509Utilities.createCertificate(
                type,
                intermediateCa.certificate,
                intermediateCa.keyPair,
                legalName.x500Principal,
                publicKey,
                nameConstraints = nameConstraints)
        return listOf(nodeCaCert, intermediateCa.certificate, rootCa.certificate)
    }

    private fun createFixedResponseRegistrationHelper(response: List<X509Certificate>, certRole: CertRole = CertRole.NODE_CA): NetworkRegistrationHelper {
        return createRegistrationHelper(certRole) { response }
    }

    private fun createRegistrationHelper(certRole: CertRole = CertRole.NODE_CA, rootAndIntermediateCA: Pair<CertificateAndKeyPair, CertificateAndKeyPair> = createDevIntermediateCaCertPath()) = createRegistrationHelper(certRole) {
        val certType = CertificateType.values().first { it.role == certRole }
        createNodeCaCertPath(rootAndIntermediateCA = rootAndIntermediateCA, publicKey = it.publicKey, type = certType)
    }

    private fun createRegistrationHelper(certRole: CertRole = CertRole.NODE_CA, dynamicResponse: (JcaPKCS10CertificationRequest) -> List<X509Certificate>): NetworkRegistrationHelper {
        val certService = rigorousMock<NetworkRegistrationService>().also {
            val requests = mutableMapOf<String, JcaPKCS10CertificationRequest>()
            doAnswer {
                val requestId = SecureHash.randomSHA256().toString()
                val request = JcaPKCS10CertificationRequest(it.getArgument<PKCS10CertificationRequest>(0))
                requests[requestId] = request
                requestId
            }.whenever(it).submitRequest(any())

            doAnswer {
                CertificateResponse(5.seconds, dynamicResponse(requests[it.getArgument(0)]!!))
            }.whenever(it).retrieveCertificates(any())
        }

        return when (certRole) {
            CertRole.NODE_CA -> NodeRegistrationHelper(config, certService, NodeRegistrationOption(config.certificatesDirectory / networkRootTrustStoreFileName, networkRootTrustStorePassword))
            CertRole.SERVICE_IDENTITY -> NetworkRegistrationHelper(
                    config.certificatesDirectory,
                    config.signingCertificateStore,
                    config.myLegalName,
                    config.emailAddress,
                    certService,
                    config.certificatesDirectory / networkRootTrustStoreFileName,
                    networkRootTrustStorePassword,
                    "${DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key",
                    CertRole.SERVICE_IDENTITY)
            else -> throw IllegalArgumentException("Unsupported cert role.")
        }
    }

    private fun saveNetworkTrustStore(rootCert: X509Certificate) {
        config.certificatesDirectory.createDirectories()
        val rootTruststorePath = config.certificatesDirectory / networkRootTrustStoreFileName
        X509KeyStore.fromFile(rootTruststorePath, networkRootTrustStorePassword, createNew = true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        }
    }
}
