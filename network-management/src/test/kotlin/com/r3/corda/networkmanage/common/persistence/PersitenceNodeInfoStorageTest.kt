package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.hashString
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersitenceNodeInfoStorageTest : TestBase() {
    private lateinit var requestStorage: CertificationRequestStorage
    private lateinit var nodeInfoStorage: NodeInfoStorage
    private lateinit var persistence: CordaPersistence
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, CordaX500Name(commonName = "Corda Node Intermediate CA", locality = "London", organisation = "R3 LTD", country = "GB"), intermediateCAKey.public)

    @Before
    fun startDb() {
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), MockServices.makeTestDatabaseProperties(), { throw UnsupportedOperationException() }, SchemaService())
        nodeInfoStorage = PersistentNodeInfoStorage(persistence)
        requestStorage = PersistentCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `test get CertificatePath`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        val request = X509Utilities.createCertificateSigningRequest(nodeInfo.legalIdentities.first().name, "my@mail.com", keyPair)

        val requestId = requestStorage.saveRequest(request)
        requestStorage.approveRequest(requestId, CertificationRequestStorage.DOORMAN_SIGNATURE)

        assertNull(nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString())))

        requestStorage.putCertificatePath(requestId, buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate()), listOf(CertificationRequestStorage.DOORMAN_SIGNATURE))

        val storedCertPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString()))
        assertNotNull(storedCertPath)

        assertEquals(clientCert.toX509Certificate(), storedCertPath!!.certificates.first())
    }

    @Test
    fun `test getNodeInfoHashes`() {
        // Create node info.
        val organisationA = "TestA"
        val requestIdA = requestStorage.saveRequest(createRequest(organisationA).first)
        requestStorage.approveRequest(requestIdA, "TestUser")
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCertA = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisationA, locality = "London", country = "GB"), keyPair.public)
        val certPathA = buildCertPath(clientCertA.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestIdA, certPathA, emptyList())
        val organisationB = "TestB"
        val requestIdB = requestStorage.saveRequest(createRequest(organisationB).first)
        requestStorage.approveRequest(requestIdB, "TestUser")
        val clientCertB = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisationB, locality = "London", country = "GB"), Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME).public)
        val certPathB = buildCertPath(clientCertB.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestIdB, certPathB, emptyList())
        val nodeInfoA = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPathA)), 1, serial = 1L)
        val nodeInfoSame = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPathA)), 1, serial = 1L)
        val nodeInfoB = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPathB)), 1, serial = 1L)

        nodeInfoStorage.putNodeInfo(nodeInfoA)
        nodeInfoStorage.putNodeInfo(nodeInfoSame)

        // getNodeInfoHashes should contain 1 hash.
        assertEquals(listOf(nodeInfoA.serialize().sha256()), nodeInfoStorage.getUnsignedNodeInfoHashes())

        nodeInfoStorage.putNodeInfo(nodeInfoB)
        // getNodeInfoHashes should contain 2 hash.
        assertEquals(listOf(nodeInfoB.serialize().sha256(), nodeInfoA.serialize().sha256()).sorted(), nodeInfoStorage.getUnsignedNodeInfoHashes().sorted())

        // Test retrieve NodeInfo.
        assertEquals(nodeInfoA, nodeInfoStorage.getNodeInfo(nodeInfoA.serialize().sha256()))
        assertEquals(nodeInfoB, nodeInfoStorage.getNodeInfo(nodeInfoB.serialize().sha256()))
    }

    @Test
    fun `same pub key with different node info`() {
        // Create node info.
        val organisation = "Test"
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.approveRequest(requestId, "TestUser")
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisation, locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestId, certPath, emptyList())

        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoSamePubKey = NodeInfo(listOf(NetworkHostAndPort("my.company2.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        nodeInfoStorage.putNodeInfo(nodeInfo)
        assertEquals(nodeInfo, nodeInfoStorage.getNodeInfo(nodeInfo.serialize().sha256()))

        // This should replace the node info.
        nodeInfoStorage.putNodeInfo(nodeInfoSamePubKey)
        // Old node info should be removed.
        assertNull(nodeInfoStorage.getNodeInfo(nodeInfo.serialize().sha256()))
        assertEquals(nodeInfoSamePubKey, nodeInfoStorage.getNodeInfo(nodeInfoSamePubKey.serialize().sha256()))
    }

    @Test
    fun `signNodeInfo associates signature to with node info`() {
        // given
        // Create node info.
        val organisation = "Test"
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.approveRequest(requestId, "TestUser")
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisation, locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestId, certPath, emptyList())

        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfo)
        // Some random bytes
        val signature = keyPair.sign(nodeInfo.serialize())

        // when
        nodeInfoStorage.signNodeInfo(nodeInfoHash, signature)

        // then
        val signedNodeInfo = nodeInfoStorage.getSignedNodeInfo(nodeInfoHash)
        assertEquals(signature, signedNodeInfo?.sig)
    }

    @Test
    fun `getUnsignedNodeInfoBytes return node info bytes`() {
        // given
        // Create node info.
        val organisation = "Test"
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.approveRequest(requestId, "TestUser")
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisation, locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestId, certPath, emptyList())

        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfo)

        // when
        val nodeInfoBytes = nodeInfoStorage.getUnsignedNodeInfoBytes()

        // then
        assertTrue(nodeInfoBytes.containsKey(nodeInfoHash))
        assertEquals(nodeInfo, nodeInfoBytes[nodeInfoHash]?.deserialize()!!)
    }
}