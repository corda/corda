package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import com.r3.corda.networkmanage.common.utils.withCert
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class DBNetworkMapStorageTest : TestBase() {
    private lateinit var networkMapStorage: NetworkMapStorage
    private lateinit var requestStorage: CertificationRequestStorage
    private lateinit var nodeInfoStorage: NodeInfoStorage
    private lateinit var persistence: CordaPersistence
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, CordaX500Name(commonName = "Corda Node Intermediate CA", locality = "London", organisation = "R3 LTD", country = "GB"), intermediateCAKey.public)

    @Before
    fun startDb() {
        persistence = configureDatabase(makeTestDataSourceProperties())
        networkMapStorage = PersistentNetworkMapStorage(persistence)
        nodeInfoStorage = PersistentNodeInfoStorage(persistence)
        requestStorage = PersistentCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `signNetworkMap creates current network map`() {
        // given
        // Create node info.
        val organisation = "Test"
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, "TestUser")
        val keyPair = Crypto.generateKeyPair()
        val clientCert = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisation, locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestId, certPath, emptyList())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        // Put signed node info data
        val nodeInfoBytes = nodeInfo.serialize()
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(SignedData(nodeInfoBytes, keyPair.sign(nodeInfoBytes)))

        // Create network parameters
        val networkParametersHash = networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList()))

        val networkMap = NetworkMap(listOf(nodeInfoHash), networkParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signatureData = intermediateCAKey.sign(serializedNetworkMap).withCert(intermediateCACert.cert)
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, signatureData)

        // when
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // then
        val persistedSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()

        assertEquals(signedNetworkMap.signature, persistedSignedNetworkMap?.signature)
        assertEquals(signedNetworkMap.verified(rootCACert.cert), persistedSignedNetworkMap?.verified(rootCACert.cert))
    }

    @Test
    fun `getLatestNetworkParameters returns last inserted`() {
        // given
        networkMapStorage.saveNetworkParameters(createNetworkParameters(minimumPlatformVersion = 1))
        networkMapStorage.saveNetworkParameters(createNetworkParameters(minimumPlatformVersion = 2))

        // when
        val latest = networkMapStorage.getLatestNetworkParameters()

        // then
        assertEquals(2, latest.minimumPlatformVersion)
    }

    @Test
    fun `getCurrentNetworkParameters returns current network map parameters`() {
        // given
        // Create network parameters
        val networkMapParametersHash = networkMapStorage.saveNetworkParameters(createNetworkParameters(1))
        // Create empty network map
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Corda", locality = "London", country = "GB"), keyPair.public)

        // Sign network map making it current network map
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signatureData = keyPair.sign(serializedNetworkMap).withCert(intermediateCert.cert)
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, signatureData)
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // Create new network parameters
        networkMapStorage.saveNetworkParameters(createNetworkParameters(2))

        // when
        val result = networkMapStorage.getCurrentNetworkParameters()

        // then
        assertEquals(1, result?.minimumPlatformVersion)
    }

    @Test
    fun `getValidNodeInfoHashes returns only valid and signed node info hashes`() {
        // given
        // Create node info.
        val organisationA = "TestA"
        val requestIdA = requestStorage.saveRequest(createRequest(organisationA).first)
        requestStorage.markRequestTicketCreated(requestIdA)
        requestStorage.approveRequest(requestIdA, "TestUser")
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCertA = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisationA, locality = "London", country = "GB"), keyPair.public)
        val certPathA = buildCertPath(clientCertA.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestIdA, certPathA, emptyList())
        val organisationB = "TestB"
        val requestIdB = requestStorage.saveRequest(createRequest(organisationB).first)
        requestStorage.markRequestTicketCreated(requestIdB)
        requestStorage.approveRequest(requestIdB, "TestUser")
        val clientCertB = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisationB, locality = "London", country = "GB"), Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME).public)
        val certPathB = buildCertPath(clientCertB.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        requestStorage.putCertificatePath(requestIdB, certPathB, emptyList())
        val nodeInfoA = NodeInfo(listOf(NetworkHostAndPort("my.companyA.com", 1234)), listOf(PartyAndCertificate(certPathA)), 1, serial = 1L)
        val nodeInfoB = NodeInfo(listOf(NetworkHostAndPort("my.companyB.com", 1234)), listOf(PartyAndCertificate(certPathB)), 1, serial = 1L)
        // Put signed node info data
        val nodeInfoABytes = nodeInfoA.serialize()
        val nodeInfoBBytes = nodeInfoB.serialize()
        val nodeInfoHashA = nodeInfoStorage.putNodeInfo(SignedData(nodeInfoABytes, keyPair.sign(nodeInfoABytes)))
        val nodeInfoHashB = nodeInfoStorage.putNodeInfo(SignedData(nodeInfoBBytes, keyPair.sign(nodeInfoBBytes)))

        // Create network parameters
        val networkParametersHash = networkMapStorage.saveNetworkParameters(createNetworkParameters())
        val networkMap = NetworkMap(listOf(nodeInfoHashA), networkParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signatureData = keyPair.sign(serializedNetworkMap).withCert(clientCertA.cert)
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, signatureData)

        // Sign network map
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // when
        val validNodeInfoHash = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)

        // then
        assertThat(validNodeInfoHash).containsOnly(nodeInfoHashA, nodeInfoHashB)
    }
}