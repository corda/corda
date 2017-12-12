package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.ALICE
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.testNodeConfiguration
import org.bouncycastle.cert.X509CertificateHolder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DoormanIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    // TODO: fix me (see commented out code in this test)
    @Ignore
    @Test
    fun `initial registration`() {
        val rootCertAndKey = createDoormanRootCertificateAndKeyPair()
        val intermediateCertAndKey = createDoormanIntermediateCertificateAndKeyPair(rootCertAndKey)

        //Start doorman server
        val doorman = startDoorman(intermediateCertAndKey, rootCertAndKey.certificate)

        // Start Corda network registration.
        val config = testNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name).also {
            val doormanHostAndPort = doorman.hostAndPort
            whenever(it.compatibilityZoneURL).thenReturn(URL("http://${doormanHostAndPort.host}:${doormanHostAndPort.port}"))
            whenever(it.emailAddress).thenReturn("iTest@R3.com")
        }
//        config.rootCaCertFile.parent.createDirectories()
//        X509Utilities.saveCertificateAsPEMFile(rootCertAndKey.certificate.toX509Certificate(), config.rootCaCertFile)
        
        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.compatibilityZoneURL!!)).buildKeystore()

        // Checks the keystore are created with the right certificates and keys.
        assert(config.nodeKeystore.toFile().exists())
        assert(config.sslKeystore.toFile().exists())
        assert(config.trustStoreFile.toFile().exists())

        val intermediateCACert = intermediateCertAndKey.certificate
        val rootCACert = rootCertAndKey.certificate

        loadKeyStore(config.nodeKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertEquals(ALICE.name.copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN).x500Principal, getX509Certificate(X509Utilities.CORDA_CLIENT_CA).subjectX500Principal)
            assertEquals(listOf(intermediateCACert.cert, rootCACert.cert), getCertificateChain(X509Utilities.CORDA_CLIENT_CA).drop(1).toList())
        }

        loadKeyStore(config.sslKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            assertEquals(ALICE.name.x500Principal, getX509Certificate(X509Utilities.CORDA_CLIENT_TLS).subjectX500Principal)
            assertEquals(listOf(intermediateCACert.cert, rootCACert.cert), getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).drop(2).toList())
        }

        loadKeyStore(config.trustStoreFile, config.trustStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertEquals(rootCACert.cert.subjectX500Principal, getX509Certificate(X509Utilities.CORDA_ROOT_CA).subjectX500Principal)
        }

        doorman.close()
    }

    // TODO: fix me (see commented out code in this test)
    @Ignore
    @Test
    fun `nodeInfo is published to the network map`() {
        // Given
        val rootCertAndKey = createDoormanRootCertificateAndKeyPair()
        val intermediateCertAndKey = createDoormanIntermediateCertificateAndKeyPair(rootCertAndKey)

        //Start doorman server
        val doorman = startDoorman(intermediateCertAndKey, rootCertAndKey.certificate)
        val doormanHostAndPort = doorman.hostAndPort

        // Start Corda network registration.
        val config = testNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name).also {
            whenever(it.compatibilityZoneURL).thenReturn(URL("http://${doormanHostAndPort.host}:${doormanHostAndPort.port}"))
            whenever(it.emailAddress).thenReturn("iTest@R3.com")
        }
//        config.rootCaCertFile.parent.createDirectories()
//        X509Utilities.saveCertificateAsPEMFile(rootCertAndKey.certificate.toX509Certificate(), config.rootCaCertFile)

        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.compatibilityZoneURL!!)).buildKeystore()

        // Publish NodeInfo
        val networkMapClient = NetworkMapClient(config.compatibilityZoneURL!!, rootCertAndKey.certificate.cert)
        val certs = loadKeyStore(config.nodeKeystore, config.keyStorePassword).getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
        val keyPair = loadKeyStore(config.nodeKeystore, config.keyStorePassword).getKeyPair(X509Utilities.CORDA_CLIENT_CA, config.keyStorePassword)
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(buildCertPath(*certs))), 1, serial = 1L)
        val nodeInfoBytes = nodeInfo.serialize()

        // When
        networkMapClient.publish(SignedData(nodeInfoBytes, keyPair.sign(nodeInfoBytes)))

        // Then
        val networkMapNodeInfo = networkMapClient.getNodeInfo(nodeInfoBytes.hash)
        assertNotNull(networkMapNodeInfo)
        assertEquals(nodeInfo, networkMapNodeInfo)

        doorman.close()
    }
}

fun createDoormanIntermediateCertificateAndKeyPair(rootCertificateAndKeyPair: CertificateAndKeyPair): CertificateAndKeyPair {
    val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCertificateAndKeyPair.certificate, rootCertificateAndKeyPair.keyPair,
            CordaX500Name(commonName = "Integration Test Corda Node Intermediate CA",
                    locality = "London",
                    country = "GB",
                    organisation = "R3 Ltd"), intermediateCAKey.public)
    return CertificateAndKeyPair(intermediateCACert, intermediateCAKey)
}

fun createDoormanRootCertificateAndKeyPair(): CertificateAndKeyPair {
    val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val rootCACert = X509Utilities.createSelfSignedCACertificate(
            CordaX500Name(commonName = "Integration Test Corda Node Root CA",
                    organisation = "R3 Ltd", locality = "London",
                    country = "GB"), rootCAKey)
    return CertificateAndKeyPair(rootCACert, rootCAKey)
}

fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}

fun startDoorman(intermediateCACertAndKey: CertificateAndKeyPair, rootCACert: X509CertificateHolder): NetworkManagementServer {
    val signer = LocalSigner(intermediateCACertAndKey.keyPair,
            arrayOf(intermediateCACertAndKey.certificate.toX509Certificate(), rootCACert.toX509Certificate()))
    //Start doorman server
    return startDoorman(signer)
}

fun startDoorman(localSigner: LocalSigner? = null): NetworkManagementServer {
    val database = configureDatabase(makeTestDataSourceProperties())
    //Start doorman server
    val server = NetworkManagementServer()
    server.start(NetworkHostAndPort("localhost", 0), database, localSigner, testNetworkParameters(emptyList()), NetworkMapConfig(1.minutes.toMillis(), 1.minutes.toMillis()), DoormanConfig(true, null, 3.seconds.toMillis()))

    return server
}