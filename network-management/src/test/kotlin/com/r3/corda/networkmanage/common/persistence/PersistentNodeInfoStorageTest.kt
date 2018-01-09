package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.signWith
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersistentNodeInfoStorageTest : TestBase() {
    private lateinit var requestStorage: CertificationRequestStorage
    private lateinit var nodeInfoStorage: PersistentNodeInfoStorage
    private lateinit var persistence: CordaPersistence

    private lateinit var rootCaCert: X509Certificate
    private lateinit var intermediateCa: CertificateAndKeyPair

    @Before
    fun startDb() {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.intermediateCa = intermediateCa
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties())
        nodeInfoStorage = PersistentNodeInfoStorage(persistence)
        requestStorage = PersistentCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `test getCertificatePath`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val name = CordaX500Name(organisation = "Test", locality = "London", country = "GB")
        val nodeCaCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCa.certificate,
                intermediateCa.keyPair,
                name.x500Principal,
                keyPair.public)

        val request = X509Utilities.createCertificateSigningRequest(name.x500Principal, "my@mail.com", keyPair)

        val requestId = requestStorage.saveRequest(request)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, CertificationRequestStorage.DOORMAN_SIGNATURE)

        assertNull(nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString())))

        requestStorage.putCertificatePath(
                requestId,
                buildCertPath(nodeCaCert, intermediateCa.certificate, rootCaCert),
                listOf(CertificationRequestStorage.DOORMAN_SIGNATURE))

        val storedCertPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString()))
        assertNotNull(storedCertPath)

        assertEquals(nodeCaCert, storedCertPath!!.certificates.first())
    }

    @Test
    fun `getNodeInfo returns persisted SignedNodeInfo using the hash of just the NodeInfo`() {
        // given
        val (nodeA) = createValidSignedNodeInfo("TestA")
        val (nodeB) = createValidSignedNodeInfo("TestB")

        // Put signed node info data
        nodeInfoStorage.putNodeInfo(nodeA)
        nodeInfoStorage.putNodeInfo(nodeB)

        // when
        val persistedSignedNodeInfoA = nodeInfoStorage.getNodeInfo(nodeA.nodeInfo.serialize().hash)
        val persistedSignedNodeInfoB = nodeInfoStorage.getNodeInfo(nodeB.nodeInfo.serialize().hash)

        // then
        assertEquals(persistedSignedNodeInfoA?.verified(), nodeA.nodeInfo)
        assertEquals(persistedSignedNodeInfoB?.verified(), nodeB.nodeInfo)
    }

    @Test
    fun `same public key with different node info`() {
        // Create node info.
        val (node1, key) = createValidSignedNodeInfo("Test", serial = 1)
        val nodeInfo2 = node1.nodeInfo.copy(serial = 2)
        val node2 = NodeInfoWithSigned(nodeInfo2.signWith(listOf(key)))

        val nodeInfo1Hash = nodeInfoStorage.putNodeInfo(node1)
        assertEquals(node1.nodeInfo, nodeInfoStorage.getNodeInfo(nodeInfo1Hash)?.verified())

        // This should replace the node info.
        nodeInfoStorage.putNodeInfo(node2)

        // Old node info should be removed.
        assertNull(nodeInfoStorage.getNodeInfo(nodeInfo1Hash))
        assertEquals(nodeInfo2, nodeInfoStorage.getNodeInfo(nodeInfo2.serialize().hash)?.verified())
    }

    @Test
    fun `putNodeInfo persists SignedNodeInfo with its signature`() {
        // given
        val (nodeInfoWithSigned) = createValidSignedNodeInfo("Test")

        // when
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfoWithSigned)

        // then
        val persistedSignedNodeInfo = nodeInfoStorage.getNodeInfo(nodeInfoHash)
        assertThat(persistedSignedNodeInfo?.signatures).isEqualTo(nodeInfoWithSigned.signedNodeInfo.signatures)
    }

    private fun createValidSignedNodeInfo(organisation: String, serial: Long = 1): Pair<NodeInfoWithSigned, PrivateKey> {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, "TestUser")
        val (identity, key) = nodeInfoBuilder.addIdentity(CordaX500Name(organisation, "London", "GB"))
        val nodeCaCertPath = X509CertificateFactory().generateCertPath(identity.certPath.certificates.drop(1))
        requestStorage.putCertificatePath(requestId, nodeCaCertPath, emptyList())
        val (_, signedNodeInfo) = nodeInfoBuilder.buildWithSigned(serial)
        return Pair(NodeInfoWithSigned(signedNodeInfo), key)
    }
}