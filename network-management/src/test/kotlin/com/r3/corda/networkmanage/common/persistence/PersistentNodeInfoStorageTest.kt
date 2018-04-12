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
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.hash
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.NodeInfoAndSigned
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
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

    @Before
    fun startDb() {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.doormanCertAndKeyPair = intermediateCa
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

        assertNull(nodeInfoStorage.getCertificatePath(keyPair.public.hash))

        requestStorage.putCertificatePath(
                requestId,
                X509Utilities.buildCertPath(nodeCaCert, doormanCertAndKeyPair.certificate, rootCaCert),
                CertificateSigningRequestStorage.DOORMAN_SIGNATURE)

        val storedCertPath = nodeInfoStorage.getCertificatePath(keyPair.public.hash)
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
        val (nodeInfoAndSigned) = createValidSignedNodeInfo("Test", requestStorage)

        // when
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)

        // then
        val persistedSignedNodeInfo = nodeInfoStorage.getNodeInfo(nodeInfoHash)
        assertThat(persistedSignedNodeInfo?.signatures).isEqualTo(nodeInfoAndSigned.signed.signatures)
    }

    @Test
    fun `publish same node info twice`() {
        fun singleNodeInfo() = persistence.transaction { session.fromQuery<NodeInfoEntity>("").singleResult }

        val (nodeInfoAndSigned) = createValidSignedNodeInfo("Test", requestStorage)
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        val nodeInfo = singleNodeInfo()
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        assertThat(nodeInfo.publishedAt).isEqualTo(singleNodeInfo().publishedAt)  // Check publishAt hasn't changed
        assertThat(singleNodeInfo().isCurrent).isTrue()
    }

    @Test
    fun `publish same node info twice after isCurrent change`() {
        fun singleNodeInfo() = persistence.transaction { session.fromQuery<NodeInfoEntity>("").singleResult }

        val (nodeInfoAndSigned) = createValidSignedNodeInfo("Test", requestStorage)
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        // Change isCurrent to false (that happens on flagDay change)
        persistence.transaction {
            val ni = singleNodeInfo()
            session.merge(ni.copy(isCurrent = false))
        }
        assertThat(singleNodeInfo().isCurrent).isFalse()
        val nodeInfo = singleNodeInfo()
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        assertThat(nodeInfo.publishedAt).isBeforeOrEqualTo(singleNodeInfo().publishedAt)
        assertThat(singleNodeInfo().isCurrent).isTrue()
    }

    @Test
    fun `accept parameters updates node info correctly`() {
        // given
        val (nodeInfoAndSigned) = createValidSignedNodeInfo("Test", requestStorage)

        // when
        val networkParameters = testNetworkParameters()
        val netParamsHash = networkParameters.serialize().hash
        networkMapStorage.saveNewParametersUpdate(networkParameters, "Update", Instant.now() + 1.days)
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
        nodeInfoStorage.ackNodeInfoParametersUpdate(nodeInfoAndSigned.nodeInfo.legalIdentities[0].owningKey, netParamsHash)

        // then
        val acceptedUpdate = nodeInfoStorage.getAcceptedParametersUpdate(nodeInfoHash)
        assertThat(acceptedUpdate?.networkParameters?.hash).isEqualTo(netParamsHash.toString())
    }

    @Test
    fun `updating node info after it's accepted network parameters`() {
        val networkParameters = testNetworkParameters()
        val netParamsHash = networkParameters.serialize().hash
        networkMapStorage.saveNewParametersUpdate(networkParameters, "Update", Instant.now() + 1.days)

        val (nodeInfoAndSigned, privateKey) = createValidSignedNodeInfo("Test", requestStorage)
        nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)

        nodeInfoStorage.ackNodeInfoParametersUpdate(nodeInfoAndSigned.nodeInfo.legalIdentities[0].owningKey, netParamsHash)

        val nodeInfo2 = nodeInfoAndSigned.nodeInfo.copy(serial = 2)
        val nodeInfoAndSigned2 = NodeInfoAndSigned(nodeInfo2.signWith(listOf(privateKey)))
        val nodeInfoHash2 = nodeInfoStorage.putNodeInfo(nodeInfoAndSigned2)

        val acceptedUpdate = nodeInfoStorage.getAcceptedParametersUpdate(nodeInfoHash2)
        assertThat(acceptedUpdate?.networkParameters?.hash).isEqualTo(netParamsHash.toString())
    }

    @Test
    fun `persist node info with multiple node CA identities`() {
        val (nodeInfo1, nodeKeyPair1) = createValidNodeInfo("Alice", requestStorage)
        val (nodeInfo2, nodeKeyPair2) = createValidNodeInfo("Bob", requestStorage)

        val multiIdentityNodeInfo = nodeInfo1.copy(legalIdentitiesAndCerts = nodeInfo1.legalIdentitiesAndCerts + nodeInfo2.legalIdentitiesAndCerts)
        val signedNodeInfo = multiIdentityNodeInfo.signWith(listOf(nodeKeyPair1, nodeKeyPair2))

        assertThatThrownBy { nodeInfoStorage.putNodeInfo(NodeInfoAndSigned(signedNodeInfo)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Require exactly 1 Node CA identity in the node-info.")
    }

    @Test
    fun `persist node info with service identity`() {
        val (nodeInfo, nodeKeyPairs) = createValidNodeInfo(requestStorage, CertRole.NODE_CA to "Alice",  CertRole.SERVICE_IDENTITY to "Alice Notary")
        val signedNodeInfo = nodeInfo.signWith(nodeKeyPairs)
        nodeInfoStorage.putNodeInfo(NodeInfoAndSigned(signedNodeInfo))
    }

    @Test
    fun `persist node info with unregistered service identity`() {
        val (nodeInfo1, nodeKeyPair1) = createValidNodeInfo("Alice", requestStorage)
        // Create a unregistered cert path with valid intermediate cert.
        val (identity, key) = TestNodeInfoBuilder().addServiceIdentity(CordaX500Name("Test", "London", "GB"), Crypto.generateKeyPair())

        val multiIdentityNodeInfo = nodeInfo1.copy(legalIdentitiesAndCerts = nodeInfo1.legalIdentitiesAndCerts + identity)
        val signedNodeInfo = multiIdentityNodeInfo.signWith(listOf(nodeKeyPair1, key))

        assertThatThrownBy { nodeInfoStorage.putNodeInfo(NodeInfoAndSigned(signedNodeInfo)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Node-info not registered with us")
    }
}

private fun createValidNodeInfo(organisation: String, storage: CertificateSigningRequestStorage): Pair<NodeInfo, PrivateKey> {
    val (nodeInfo, keys) = createValidNodeInfo(storage, CertRole.NODE_CA to organisation)
    return Pair(nodeInfo, keys.single())
}

private fun createValidNodeInfo(storage: CertificateSigningRequestStorage, vararg identities: Pair<CertRole, String>): Pair<NodeInfo, List<PrivateKey>> {
    val nodeInfoBuilder = TestNodeInfoBuilder()
    val keys = identities.map { (certRole, name) ->
        val (csr, nodeKeyPair) = createRequest(name, certRole = certRole)
        val requestId = storage.saveRequest(csr)
        storage.markRequestTicketCreated(requestId)
        storage.approveRequest(requestId, "TestUser")
        val (identity, key) = when (certRole) {
            CertRole.NODE_CA -> nodeInfoBuilder.addLegalIdentity(CordaX500Name.build(X500Principal(csr.subject.encoded)), nodeKeyPair)
            CertRole.SERVICE_IDENTITY -> nodeInfoBuilder.addServiceIdentity(CordaX500Name.build(X500Principal(csr.subject.encoded)), nodeKeyPair)
            else -> throw IllegalArgumentException("Unsupported cert role $certRole.")
        }
        storage.putCertificatePath(requestId, identity.certPath, "Test")
        key
    }
    return Pair(nodeInfoBuilder.build(), keys)
}

internal fun createValidSignedNodeInfo(organisation: String, storage: CertificateSigningRequestStorage): Pair<NodeInfoAndSigned, PrivateKey> {
    val (nodeInfo, key) = createValidNodeInfo(organisation, storage)
    return Pair(NodeInfoAndSigned(nodeInfo.signWith(listOf(key))), key)
}
