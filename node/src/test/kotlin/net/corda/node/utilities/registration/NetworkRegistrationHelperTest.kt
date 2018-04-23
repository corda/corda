/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.core.internal.div
import net.corda.core.internal.x500Name
import net.corda.core.utilities.seconds
import net.corda.node.NodeRegistrationOption
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
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

class NetworkRegistrationHelperTest {
    private val fs = Jimfs.newFileSystem(unix())
    private val requestId = SecureHash.randomSHA256().toString()
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

        saveNetworkTrustStore(nodeCaCertPath.last())
        createRegistrationHelper(nodeCaCertPath).buildKeystore()

        val nodeKeystore = config.loadNodeKeyStore()
        val sslKeystore = config.loadSslKeyStore()
        val trustStore = config.loadTrustStore()

        nodeKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_TLS))
            assertThat(getCertificateChain(X509Utilities.CORDA_CLIENT_CA)).containsExactlyElementsOf(nodeCaCertPath)
        }

        sslKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            val nodeTlsCertChain = getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
            assertThat(nodeTlsCertChain).hasSize(4)
            // The TLS cert has the same subject as the node CA cert
            assertThat(CordaX500Name.build(nodeTlsCertChain[0].subjectX500Principal)).isEqualTo(nodeLegalName)
            assertThat(nodeTlsCertChain.drop(1)).containsExactlyElementsOf(nodeCaCertPath)
        }

        trustStore.run {
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
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
        saveNetworkTrustStore(nodeCaCertPath.last())
        val registrationHelper = createRegistrationHelper(nodeCaCertPath)
        assertThatExceptionOfType(CertificateRequestException::class.java)
                .isThrownBy { registrationHelper.buildKeystore() }
                .withMessageContaining(CertificateType.TLS.toString())
    }

    @Test
    fun `node CA with incorrect subject`() {
        val invalidName = CordaX500Name("Foo", "MU", "GB")
        val nodeCaCertPath = createNodeCaCertPath(legalName = invalidName)
        saveNetworkTrustStore(nodeCaCertPath.last())
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
        saveNetworkTrustStore(wrongRootCert)
        val registrationHelper = createRegistrationHelper(createNodeCaCertPath())
        assertThatThrownBy {
            registrationHelper.buildKeystore()
        }.isInstanceOf(CertPathValidatorException::class.java)
    }

    @Test
    fun `create service identity cert`() {
        assertThat(config.nodeKeystore).doesNotExist()
        assertThat(config.sslKeystore).doesNotExist()
        assertThat(config.trustStoreFile).doesNotExist()

        val serviceIdentityCertPath = createServiceIdentityCertPath()

        saveNetworkTrustStore(serviceIdentityCertPath.last())
        createRegistrationHelper(serviceIdentityCertPath).buildKeystore()

        val nodeKeystore = config.loadNodeKeyStore()
        val trustStore = config.loadTrustStore()
        assertThat(config.sslKeystore).doesNotExist()

        val serviceIdentityAlias = "${DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key"

        nodeKeystore.run {
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(contains(X509Utilities.CORDA_ROOT_CA))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_TLS))
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertThat(getCertificateChain(serviceIdentityAlias)).containsExactlyElementsOf(serviceIdentityCertPath)
        }

        trustStore.run {
            assertFalse(contains(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(contains(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertThat(getCertificate(X509Utilities.CORDA_ROOT_CA)).isEqualTo(serviceIdentityCertPath.last())
        }
    }

    private fun createNodeCaCertPath(type: CertificateType = CertificateType.NODE_CA,
                                     legalName: CordaX500Name = nodeLegalName): List<X509Certificate> {
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
        return listOf(nodeCaCert, intermediateCa.certificate, rootCa.certificate)
    }

    private fun createServiceIdentityCertPath(type: CertificateType = CertificateType.SERVICE_IDENTITY,
                                              legalName: CordaX500Name = nodeLegalName): List<X509Certificate> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        val keyPair = Crypto.generateKeyPair()
        val serviceIdentityCert = X509Utilities.createCertificate(
                type,
                intermediateCa.certificate,
                intermediateCa.keyPair,
                legalName.x500Principal,
                keyPair.public)
        return listOf(serviceIdentityCert, intermediateCa.certificate, rootCa.certificate)
    }

    private fun createRegistrationHelper(response: List<X509Certificate>): NetworkRegistrationHelper {
        val certService = rigorousMock<NetworkRegistrationService>().also {
            doReturn(requestId).whenever(it).submitRequest(any())
            doReturn(CertificateResponse(5.seconds, response)).whenever(it).retrieveCertificates(eq(requestId))
        }
        return NetworkRegistrationHelper(config, certService, NodeRegistrationOption(config.certificatesDirectory / networkRootTrustStoreFileName, networkRootTrustStorePassword))
    }

    private fun saveNetworkTrustStore(rootCert: X509Certificate) {
        config.certificatesDirectory.createDirectories()
        val rootTruststorePath = config.certificatesDirectory / networkRootTrustStoreFileName
        X509KeyStore.fromFile(rootTruststorePath, networkRootTrustStorePassword, createNew = true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        }
    }
}
