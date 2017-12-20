package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.withCert
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.TestNodeInfoBuilder
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
        val signedNodeInfo = createValidSignedNodeInfo("Test")
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(signedNodeInfo)

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

        // Sign network map making it current network map
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signatureData = intermediateCAKey.sign(serializedNetworkMap).withCert(intermediateCACert.cert)
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
        // Create node infos.
        val signedNodeInfoA = createValidSignedNodeInfo("TestA")
        val signedNodeInfoB = createValidSignedNodeInfo("TestB")

        // Put signed node info data
        val nodeInfoHashA = nodeInfoStorage.putNodeInfo(signedNodeInfoA)
        val nodeInfoHashB = nodeInfoStorage.putNodeInfo(signedNodeInfoB)

        // Create network parameters
        val networkParametersHash = networkMapStorage.saveNetworkParameters(createNetworkParameters())
        val networkMap = NetworkMap(listOf(nodeInfoHashA), networkParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signatureData = intermediateCAKey.sign(serializedNetworkMap).withCert(intermediateCACert.cert)
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, signatureData)

        // Sign network map
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // when
        val validNodeInfoHash = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)

        // then
        assertThat(validNodeInfoHash).containsOnly(nodeInfoHashA, nodeInfoHashB)
    }

    private fun createValidSignedNodeInfo(organisation: String): SignedNodeInfo {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, "TestUser")
        val (identity) = nodeInfoBuilder.addIdentity(CordaX500Name(organisation, "London", "GB"))
        val nodeCaCertPath = X509CertificateFactory().generateCertPath(identity.certPath.certificates.drop(1))
        requestStorage.putCertificatePath(requestId, nodeCaCertPath, emptyList())
        return nodeInfoBuilder.buildWithSigned().second
    }
}