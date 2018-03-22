/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
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
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentNodeInfoStorageTest : TestBase() {
    private lateinit var requestStorage: CertificateSigningRequestStorage
    private lateinit var nodeInfoStorage: PersistentNodeInfoStorage
    private lateinit var networkMapStorage: PersistentNetworkMapStorage
    private lateinit var persistence: CordaPersistence
    private lateinit var rootCaCert: X509Certificate
    private lateinit var doormanCertAndKeyPair: CertificateAndKeyPair
    private lateinit var networkMapCertAndKeyPair: CertificateAndKeyPair

    @Before
    fun startDb() {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.doormanCertAndKeyPair = intermediateCa
        networkMapCertAndKeyPair = createDevNetworkMapCa(rootCa)
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        nodeInfoStorage = PersistentNodeInfoStorage(persistence)
        requestStorage = PersistentCertificateSigningRequestStorage(persistence)
        networkMapStorage = PersistentNetworkMapStorage(persistence)
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
                doormanCertAndKeyPair.certificate,
                doormanCertAndKeyPair.keyPair,
                name.x500Principal,
                keyPair.public)

        val request = X509Utilities.createCertificateSigningRequest(name.x500Principal, "my@mail.com", keyPair)

        val requestId = requestStorage.saveRequest(request)
        requestStorage.markRequestTicketCreated(requestId)
        requestStorage.approveRequest(requestId, CertificateSigningRequestStorage.DOORMAN_SIGNATURE)

        assertNull(nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString())))

        requestStorage.putCertificatePath(
                requestId,
                X509Utilities.buildCertPath(nodeCaCert, doormanCertAndKeyPair.certificate, rootCaCert),
                CertificateSigningRequestStorage.DOORMAN_SIGNATURE)

        val storedCertPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(keyPair.public.hashString()))
        assertNotNull(storedCertPath)

        assertEquals(nodeCaCert, storedCertPath!!.certificates.first())
    }

    @Test
    fun `getNodeInfo returns persisted SignedNodeInfo using the hash of just the NodeInfo`() {
        // given
        val (nodeA) = createValidSignedNodeInfo("TestA", requestStorage)
        val (nodeB) = createValidSignedNodeInfo("TestB", requestStorage)

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
        val (node1, key) = createValidSignedNodeInfo("Test", requestStorage)
        val nodeInfo2 = node1.nodeInfo.copy(serial = 2)
        val node2 = NodeInfoAndSigned(nodeInfo2.signWith(listOf(key)))

        val nodeInfo1Hash = nodeInfoStorage.putNodeInfo(node1)
        assertEquals(node1.nodeInfo, nodeInfoStorage.getNodeInfo(nodeInfo1Hash)?.verified())
        assertTrue(networkMapStorage.getActiveNodeInfoHashes().contains(nodeInfo1Hash))

        // This should replace the node info.
        val nodeInfo2Hash = nodeInfoStorage.putNodeInfo(node2)

        // Old node info should be removed from list of current node info hashes, but still accessible if required.
        assertThat(networkMapStorage.getActiveNodeInfoHashes()).doesNotContain(nodeInfo1Hash)
        assertThat(networkMapStorage.getActiveNodeInfoHashes()).contains(nodeInfo2Hash)
        assertNotNull(nodeInfoStorage.getNodeInfo(nodeInfo1Hash))
        assertEquals(nodeInfo2, nodeInfoStorage.getNodeInfo(nodeInfo2.serialize().hash)?.verified())
    }

    @Test
    fun `putNodeInfo persists SignedNodeInfo with its signature`() {
        // given
        val (nodeInfoWithSigned) = createValidSignedNodeInfo("Test", requestStorage)

        // when
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfoWithSigned)

        // then
        val persistedSignedNodeInfo = nodeInfoStorage.getNodeInfo(nodeInfoHash)
        assertThat(persistedSignedNodeInfo?.signatures).isEqualTo(nodeInfoWithSigned.signed.signatures)
    }

    @Test
    fun `accept parameters updates node info correctly`() {
        // given
        val (nodeInfoAndSigned) = createValidSignedNodeInfo("Test", requestStorage)

        // when
        val networkParameters = testNetworkParameters()
        val sigWithCert = networkMapCertAndKeyPair.sign(networkParameters).sig
        val netParamsHash = networkMapStorage.saveNetworkParameters(networkParameters, sigWithCert)
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        nodeInfoStorage.ackNodeInfoParametersUpdate(nodeInfoAndSigned.nodeInfo.legalIdentities[0].owningKey.encoded.sha256(), netParamsHash)

        // then
        val acceptedNetworkParameters = nodeInfoStorage.getAcceptedNetworkParameters(nodeInfoHash)
        assertThat(acceptedNetworkParameters?.hash).isEqualTo(netParamsHash.toString())
    }

    @Test
    fun `updating node info after it's accepted network parameters`() {
        val networkParameters = testNetworkParameters()
        val sigWithCert = networkMapCertAndKeyPair.sign(networkParameters).sig
        val netParamsHash = networkMapStorage.saveNetworkParameters(networkParameters, sigWithCert)

        val (nodeInfoAndSigned, privateKey) = createValidSignedNodeInfo("Test", requestStorage)
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)

        nodeInfoStorage.ackNodeInfoParametersUpdate(nodeInfoAndSigned.nodeInfo.legalIdentities[0].owningKey.encoded.sha256(), netParamsHash)

        val nodeInfo2 = nodeInfoAndSigned.nodeInfo.copy(serial = 2)
        val nodeInfoAndSigned2 = NodeInfoAndSigned(nodeInfo2.signWith(listOf(privateKey)))
        val nodeInfoHash2 = nodeInfoStorage.putNodeInfo(nodeInfoAndSigned2)

        val acceptedNetworkParameters = nodeInfoStorage.getAcceptedNetworkParameters(nodeInfoHash2)
        assertThat(acceptedNetworkParameters?.hash).isEqualTo(netParamsHash.toString())
    }
}

internal fun createValidSignedNodeInfo(organisation: String,
                                       storage: CertificateSigningRequestStorage): Pair<NodeInfoAndSigned, PrivateKey> {
    val (csr, nodeKeyPair) = createRequest(organisation, certRole = CertRole.NODE_CA)
    val requestId = storage.saveRequest(csr)
    storage.markRequestTicketCreated(requestId)
    storage.approveRequest(requestId, "TestUser")
    val nodeInfoBuilder = TestNodeInfoBuilder()
    val (identity, key) = nodeInfoBuilder.addIdentity(CordaX500Name.build(X500Principal(csr.subject.encoded)), nodeKeyPair)
    storage.putCertificatePath(requestId, identity.certPath, "Test")
    val (_, signedNodeInfo) = nodeInfoBuilder.buildWithSigned(1)
    return Pair(NodeInfoAndSigned(signedNodeInfo), key)
}