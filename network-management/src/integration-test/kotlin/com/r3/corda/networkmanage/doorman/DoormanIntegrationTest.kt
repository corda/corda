package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sign
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.ALICE_NAME
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.rigorousMock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DoormanIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    @Test
    fun `initial registration`() {
        val rootCertAndKey = createDoormanRootCertificateAndKeyPair()
        val intermediateCertAndKey = createDoormanIntermediateCertificateAndKeyPair(rootCertAndKey)

        //Start doorman server
        val doorman = startDoorman(intermediateCertAndKey, rootCertAndKey.certificate)
        val doormanHostAndPort = doorman.hostAndPort
        // Start Corda network registration.
        val config = createConfig().also {
            doReturn(URL("http://${doormanHostAndPort.host}:${doormanHostAndPort.port}")).whenever(it).compatibilityZoneURL
        }
        config.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCertAndKey.certificate)
            it.save(config.trustStoreFile, config.trustStorePassword)
        }
        
        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.compatibilityZoneURL!!)).buildKeystore()

        // Checks the keystore are created with the right certificates and keys.
        assert(config.nodeKeystore.toFile().exists())
        assert(config.sslKeystore.toFile().exists())
        assert(config.trustStoreFile.toFile().exists())

        val intermediateCACert = intermediateCertAndKey.certificate
        val rootCACert = rootCertAndKey.certificate

        loadKeyStore(config.nodeKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertEquals(ALICE_NAME.x500Principal, getX509Certificate(X509Utilities.CORDA_CLIENT_CA).subjectX500Principal)
            assertEquals(listOf(intermediateCACert, rootCACert), getCertificateChain(X509Utilities.CORDA_CLIENT_CA).drop(1).toList())
        }

        loadKeyStore(config.sslKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            assertEquals(ALICE_NAME.x500Principal, getX509Certificate(X509Utilities.CORDA_CLIENT_TLS).subjectX500Principal)
            assertEquals(listOf(intermediateCACert, rootCACert), getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).drop(2).toList())
        }

        loadKeyStore(config.trustStoreFile, config.trustStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertEquals(rootCACert.subjectX500Principal, getX509Certificate(X509Utilities.CORDA_ROOT_CA).subjectX500Principal)
        }

        doorman.close()
    }

    @Test
    fun `nodeInfo is published to the network map`() {
        // Given
        val rootCertAndKey = createDoormanRootCertificateAndKeyPair()
        val intermediateCertAndKey = createDoormanIntermediateCertificateAndKeyPair(rootCertAndKey)

        //Start doorman server
        val doorman = startDoorman(intermediateCertAndKey, rootCertAndKey.certificate)
        val doormanHostAndPort = doorman.hostAndPort

        // Start Corda network registration.
        val config = createConfig().also {
            doReturn(URL("http://${doormanHostAndPort.host}:${doormanHostAndPort.port}")).whenever(it).compatibilityZoneURL
        }
        config.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCertAndKey.certificate)
            it.save(config.trustStoreFile, config.trustStorePassword)
        }

        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.compatibilityZoneURL!!)).buildKeystore()

        // Publish NodeInfo
        val networkMapClient = NetworkMapClient(config.compatibilityZoneURL!!, rootCertAndKey.certificate)

        val keyStore = loadKeyStore(config.nodeKeystore, config.keyStorePassword)
        val clientCertPath = keyStore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
        val clientCA = keyStore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, config.keyStorePassword)
        val identityKeyPair = Crypto.generateKeyPair()
        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                clientCA.certificate,
                clientCA.keyPair,
                ALICE_NAME.x500Principal,
                identityKeyPair.public)
        val certPath = X509CertificateFactory().generateCertPath(identityCert, *clientCertPath)
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoBytes = nodeInfo.serialize()

        // When
        val signedNodeInfo = SignedNodeInfo(nodeInfoBytes, listOf(identityKeyPair.private.sign(nodeInfoBytes.bytes)))
        networkMapClient.publish(signedNodeInfo)

        // Then
        val networkMapNodeInfo = networkMapClient.getNodeInfo(nodeInfoBytes.hash)
        assertNotNull(networkMapNodeInfo)
        assertEquals(nodeInfo, networkMapNodeInfo)

        doorman.close()
    }

    private fun createConfig(): NodeConfiguration {
        return rigorousMock<NodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(it.baseDirectory / "certificates").whenever(it).certificatesDirectory
            doReturn(it.certificatesDirectory / "truststore.jks").whenever(it).trustStoreFile
            doReturn(it.certificatesDirectory / "nodekeystore.jks").whenever(it).nodeKeystore
            doReturn(it.certificatesDirectory / "sslkeystore.jks").whenever(it).sslKeystore
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn("iTest@R3.com").whenever(it).emailAddress
        }
    }
}


fun createDoormanIntermediateCertificateAndKeyPair(rootCa: CertificateAndKeyPair): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCACert = X509Utilities.createCertificate(
            CertificateType.INTERMEDIATE_CA,
            rootCa.certificate,
            rootCa.keyPair,
            X500Principal("CN=Integration Test Corda Node Intermediate CA,O=R3 Ltd,L=London,C=GB"),
            keyPair.public)
    return CertificateAndKeyPair(intermediateCACert, keyPair)
}

fun createDoormanRootCertificateAndKeyPair(): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val rootCaCert = X509Utilities.createSelfSignedCACertificate(
            X500Principal("CN=Integration Test Corda Node Root CA,O=R3 Ltd,L=London,C=GB"),
            keyPair)
    return CertificateAndKeyPair(rootCaCert, keyPair)
}

fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}

fun startDoorman(intermediateCACertAndKey: CertificateAndKeyPair, rootCACert: X509Certificate): NetworkManagementServer {
    val signer = LocalSigner(intermediateCACertAndKey.keyPair, arrayOf(intermediateCACertAndKey.certificate, rootCACert))
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