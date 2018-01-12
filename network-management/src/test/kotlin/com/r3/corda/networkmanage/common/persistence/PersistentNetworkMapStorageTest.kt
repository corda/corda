package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.signWithCert
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals

class PersistentNetworkMapStorageTest : TestBase() {
    private lateinit var persistence: CordaPersistence
    private lateinit var networkMapStorage: PersistentNetworkMapStorage
    private lateinit var nodeInfoStorage: PersistentNodeInfoStorage
    private lateinit var requestStorage: PersistentCertificateRequestStorage

    private lateinit var rootCaCert: X509Certificate
    private lateinit var networkMapCa: CertificateAndKeyPair

    @Before
    fun startDb() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        networkMapCa = createDevNetworkMapCa(rootCa)
        persistence = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        networkMapStorage = PersistentNetworkMapStorage(persistence, LocalSigner(networkMapCa.keyPair, arrayOf(networkMapCa.certificate, rootCaCert)))
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
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)

        // when
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // then
        val persistedSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()

        assertEquals(signedNetworkMap.sig, persistedSignedNetworkMap?.sig)
        assertEquals(signedNetworkMap.verifiedNetworkMapCert(rootCaCert), persistedSignedNetworkMap?.verifiedNetworkMapCert(rootCaCert))
    }

    @Test
    fun `getLatestNetworkParameters returns last inserted`() {
        // given
        networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList(), minimumPlatformVersion = 1))
        networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList(), minimumPlatformVersion = 2))

        // when
        val latest = networkMapStorage.getLatestNetworkParameters()

        // then
        assertEquals(2, latest.minimumPlatformVersion)
    }

    @Test
    fun `getCurrentNetworkParameters returns current network map parameters`() {
        // given
        // Create network parameters
        val networkParametersHash = networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList()))
        // Create empty network map

        // Sign network map making it current network map
        val networkMap = NetworkMap(emptyList(), networkParametersHash)
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // Create new network parameters
        networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList(), minimumPlatformVersion = 2))

        // when
        val result = networkMapStorage.getCurrentNetworkParameters()

        // then
        assertEquals(1, result?.minimumPlatformVersion)
    }

    // This test will probably won't be needed when we remove the explicit use of LocalSigner
    @Test
    fun `getSignedNetworkParameters uses the local signer to return a signed object`() {
        val networkParameters = testNetworkParameters(emptyList())
        val netParamsHash = networkMapStorage.saveNetworkParameters(networkParameters)
        val signedNetworkParameters = networkMapStorage.getSignedNetworkParameters(netParamsHash)
        assertThat(signedNetworkParameters?.verifiedNetworkMapCert(rootCaCert)).isEqualTo(networkParameters)
        assertThat(signedNetworkParameters?.sig?.by).isEqualTo(networkMapCa.certificate)
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
        val networkParametersHash = networkMapStorage.saveNetworkParameters(testNetworkParameters(emptyList()))
        val networkMap = NetworkMap(listOf(nodeInfoHashA), networkParametersHash)
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)

        // Sign network map
        networkMapStorage.saveNetworkMap(signedNetworkMap)

        // when
        val validNodeInfoHash = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)

        // then
        assertThat(validNodeInfoHash).containsOnly(nodeInfoHashA, nodeInfoHashB)
    }

    private fun createValidSignedNodeInfo(organisation: String): NodeInfoWithSigned {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, "TestUser")
        val (identity) = nodeInfoBuilder.addIdentity(CordaX500Name(organisation, "London", "GB"))
        val nodeCaCertPath = X509CertificateFactory().generateCertPath(identity.certPath.certificates.drop(1))
        requestStorage.putCertificatePath(requestId, nodeCaCertPath, emptyList())
        return NodeInfoWithSigned(nodeInfoBuilder.buildWithSigned().second)
    }
}