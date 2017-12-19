package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.signWith
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersitenceNodeInfoStorageTest : TestBase() {
    private lateinit var requestStorage: CertificationRequestStorage
    private lateinit var nodeInfoStorage: PersistentNodeInfoStorage
    private lateinit var persistence: CordaPersistence

    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, CordaX500Name(commonName = "Corda Node Intermediate CA", locality = "London", organisation = "R3 LTD", country = "GB"), intermediateCAKey.public)

    @Before
    fun startDb() {
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
        val nodeCaCert = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCACert, intermediateCAKey, name, keyPair.public)

        val request = X509Utilities.createCertificateSigningRequest(name, "my@mail.com", keyPair)

        val requestId = requestStorage.saveRequest(request)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, CertificationRequestStorage.DOORMAN_SIGNATURE)

        assertNull(nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString())))

        requestStorage.putCertificatePath(requestId, buildCertPath(nodeCaCert.cert, intermediateCACert.cert, rootCACert.cert), listOf(CertificationRequestStorage.DOORMAN_SIGNATURE))

        val storedCertPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString()))
        assertNotNull(storedCertPath)

        assertEquals(nodeCaCert.cert, storedCertPath!!.certificates.first())
    }

    @Test
    fun `getNodeInfo returns persisted SignedNodeInfo using the hash of just the NodeInfo`() {
        // given
        val (nodeInfoA, signedNodeInfoA) = createValidSignedNodeInfo("TestA")
        val (nodeInfoB, signedNodeInfoB) = createValidSignedNodeInfo("TestB")

        // Put signed node info data
        nodeInfoStorage.putNodeInfo(signedNodeInfoA)
        nodeInfoStorage.putNodeInfo(signedNodeInfoB)

        // when
        val persistedSignedNodeInfoA = nodeInfoStorage.getNodeInfo(nodeInfoA.serialize().hash)
        val persistedSignedNodeInfoB = nodeInfoStorage.getNodeInfo(nodeInfoB.serialize().hash)

        // then
        assertEquals(persistedSignedNodeInfoA?.verified(), nodeInfoA)
        assertEquals(persistedSignedNodeInfoB?.verified(), nodeInfoB)
    }

    @Test
    fun `same public key with different node info`() {
        // Create node info.
        val (nodeInfo1, signedNodeInfo1, key) = createValidSignedNodeInfo("Test", serial = 1)
        val nodeInfo2 = nodeInfo1.copy(serial = 2)
        val signedNodeInfo2 = nodeInfo2.signWith(listOf(key))

        val nodeInfo1Hash = nodeInfoStorage.putNodeInfo(signedNodeInfo1)
        assertEquals(nodeInfo1, nodeInfoStorage.getNodeInfo(nodeInfo1Hash)?.verified())

        // This should replace the node info.
        nodeInfoStorage.putNodeInfo(signedNodeInfo2)

        // Old node info should be removed.
        assertNull(nodeInfoStorage.getNodeInfo(nodeInfo1Hash))
        assertEquals(nodeInfo2, nodeInfoStorage.getNodeInfo(nodeInfo2.serialize().hash)?.verified())
    }

    @Test
    fun `putNodeInfo persists SignedNodeInfo with its signature`() {
        // given
        val (_, signedNodeInfo) = createValidSignedNodeInfo("Test")

        // when
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(signedNodeInfo)

        // then
        val persistedSignedNodeInfo = nodeInfoStorage.getNodeInfo(nodeInfoHash)
        assertThat(persistedSignedNodeInfo?.signatures).isEqualTo(signedNodeInfo.signatures)
    }

    private fun createValidSignedNodeInfo(organisation: String, serial: Long = 1): Triple<NodeInfo, SignedNodeInfo, PrivateKey> {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val requestId = requestStorage.saveRequest(createRequest(organisation).first)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, "TestUser")
        val (identity, key) = nodeInfoBuilder.addIdentity(CordaX500Name(organisation, "London", "GB"))
        val nodeCaCertPath = X509CertificateFactory().generateCertPath(identity.certPath.certificates.drop(1))
        requestStorage.putCertificatePath(requestId, nodeCaCertPath, emptyList())
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned(serial)
        return Triple(nodeInfo, signedNodeInfo, key)
    }
}