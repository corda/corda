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
import com.r3.corda.networkmanage.common.persistence.entity.ParametersUpdateEntity
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.serialize
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import java.time.Instant

class PersistentNetworkMapStorageTest : TestBase() {
    private lateinit var persistence: CordaPersistence
    private lateinit var networkMapStorage: PersistentNetworkMapStorage
    private lateinit var nodeInfoStorage: PersistentNodeInfoStorage
    private lateinit var requestStorage: PersistentCertificateSigningRequestStorage

    private lateinit var rootCaCert: X509Certificate
    private lateinit var networkMapCertAndKeyPair: CertificateAndKeyPair

    @Before
    fun startDb() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        networkMapCertAndKeyPair = createDevNetworkMapCa(rootCa)
        persistence = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        networkMapStorage = PersistentNetworkMapStorage(persistence)
        nodeInfoStorage = PersistentNodeInfoStorage(persistence)
        requestStorage = PersistentCertificateSigningRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `create active network map`() {
        // given
        // Create node info.
        val (signedNodeInfo) = createValidSignedNodeInfo("Test", requestStorage)
        val nodeInfoHash = nodeInfoStorage.putNodeInfo(signedNodeInfo)

        // Create network parameters
        val networkParameters = testNetworkParameters(maxTransactionSize = 1234567)
        val networkParametersSig = networkMapCertAndKeyPair.sign(networkParameters).sig
        val networkParametersHash = networkMapStorage.saveNetworkParameters(networkParameters, networkParametersSig).hash
        val networkMap = NetworkMap(listOf(nodeInfoHash), SecureHash.parse(networkParametersHash), null)
        val networkMapAndSigned = NetworkMapAndSigned(networkMap) { networkMapCertAndKeyPair.sign(networkMap).sig }

        // when
        networkMapStorage.saveNewActiveNetworkMap(networkMapAndSigned)

        // then
        val activeNetworkMapEntity = networkMapStorage.getActiveNetworkMap()!!
        val activeSignedNetworkMap = activeNetworkMapEntity.toSignedNetworkMap()
        val activeNetworkMap = activeSignedNetworkMap.verifiedNetworkMapCert(rootCaCert)
        val activeNetworkParametersEntity = activeNetworkMapEntity.networkParameters
        val activeSignedNetworkParameters = activeNetworkParametersEntity.toSignedNetworkParameters()
        val activeNetworkParameters = activeSignedNetworkParameters.verifiedNetworkMapCert(rootCaCert)

        assertThat(activeNetworkMap).isEqualTo(networkMap)
        assertThat(activeSignedNetworkMap.sig).isEqualTo(networkMapAndSigned.signed.sig)
        assertThat(activeNetworkParameters).isEqualTo(networkParameters)
        assertThat(activeSignedNetworkParameters.sig).isEqualTo(networkParametersSig)
        assertThat(activeNetworkParametersEntity.hash)
                .isEqualTo(activeNetworkMap.networkParameterHash.toString())
                .isEqualTo(networkParametersHash)
    }

    @Test
    fun `getLatestNetworkParameters returns last inserted`() {
        val params1 = testNetworkParameters(minimumPlatformVersion = 1)
        val params2 = testNetworkParameters(minimumPlatformVersion = 2)
        networkMapStorage.saveNetworkParameters(params1, networkMapCertAndKeyPair.sign(params1).sig)
        // We may have not signed them yet.
        networkMapStorage.saveNetworkParameters(params2, null)

        assertThat(networkMapStorage.getLatestNetworkParameters()?.networkParameters).isEqualTo(params2)
    }

    @Test
    fun `getValidNodeInfoHashes returns only for current node-infos`() {
        // given
        // Create node infos.
        val (signedNodeInfoA) = createValidSignedNodeInfo("TestA", requestStorage)
        val (signedNodeInfoB) = createValidSignedNodeInfo("TestB", requestStorage)

        // Put signed node info data
        val nodeInfoHashA = nodeInfoStorage.putNodeInfo(signedNodeInfoA)
        val nodeInfoHashB = nodeInfoStorage.putNodeInfo(signedNodeInfoB)

        persistence.transaction {
            val entity = session.find(NodeInfoEntity::class.java, nodeInfoHashA.toString())
            session.merge(entity.copy(isCurrent = false))
        }

        // when
        val validNodeInfoHashes = networkMapStorage.getActiveNodeInfoHashes()

        // then
        assertThat(validNodeInfoHashes).containsOnly(nodeInfoHashB)
    }

    @Test
    fun `saveNewParametersUpdate marks update as NEW and persists network parameters as the latest`() {
        val networkParameters = testNetworkParameters()
        val updateDeadline = Instant.now() + 10.days
        networkMapStorage.saveNewParametersUpdate(networkParameters, "Update 1", updateDeadline)
        val parameterUpdate = networkMapStorage.getCurrentParametersUpdate()!!
        assertThat(parameterUpdate.description).isEqualTo("Update 1")
        assertThat(parameterUpdate.updateDeadline).isEqualTo(updateDeadline)
        assertThat(parameterUpdate.status).isEqualTo(UpdateStatus.NEW)
        assertThat(parameterUpdate.networkParameters.networkParameters).isEqualTo(networkParameters)
        assertThat(networkMapStorage.getLatestNetworkParameters()?.networkParameters).isEqualTo(networkParameters)
    }

    @Test
    fun `saveNewParametersUpdate marks previous update as cancelled`() {
        networkMapStorage.saveNewParametersUpdate(testNetworkParameters(epoch = 1), "Update 1", Instant.now() + 1.days)
        networkMapStorage.saveNewParametersUpdate(testNetworkParameters(epoch = 2), "Update of update", Instant.now() + 2.days)
        val firstUpdate = persistence.transaction {
            session.fromQuery<ParametersUpdateEntity>("u where u.description = 'Update 1'").singleResult
        }
        assertThat(firstUpdate.status).isEqualTo(UpdateStatus.CANCELLED)
        assertThat(networkMapStorage.getCurrentParametersUpdate()?.description).isEqualTo("Update of update")
    }

    @Test
    fun `switch ParametersUpdate on flag day`() {
        // Update
        val networkParameters1 = testNetworkParameters()
        val updateDeadline = Instant.now() + 10.days
        networkMapStorage.saveNewParametersUpdate(networkParameters1, "Update 1", updateDeadline)
        // given
        val (signedNodeInfoA) = createValidSignedNodeInfo("TestA", requestStorage) // null as acceptedParametersUpdate
        val (signedNodeInfoB) = createValidSignedNodeInfo("TestB", requestStorage) // accepts update

        // Put signed node info data
        nodeInfoStorage.putNodeInfo(signedNodeInfoA)
        val nodeInfoHashB = nodeInfoStorage.putNodeInfo(signedNodeInfoB)

        nodeInfoStorage.ackNodeInfoParametersUpdate(signedNodeInfoB.nodeInfo.legalIdentities[0].owningKey, networkParameters1.serialize().hash)
        val parameterUpdate = networkMapStorage.getCurrentParametersUpdate()!!
        networkMapStorage.switchFlagDay(parameterUpdate)
        // when
        val validNodeInfoHashes = networkMapStorage.getActiveNodeInfoHashes()
        // then
        assertThat(validNodeInfoHashes).containsOnly(nodeInfoHashB)
    }

    @Test
    fun `accept second set of parameters and switch on flag day`() {
        // Update 1
        val networkParameters1 = testNetworkParameters()
        val updateDeadline = Instant.now() + 10.days
        networkMapStorage.saveNewParametersUpdate(networkParameters1, "Update 1", updateDeadline)
        // given
        val (signedNodeInfoA) = createValidSignedNodeInfo("TestA", requestStorage) // Update 1 as acceptedParametersUpdate
        val (signedNodeInfoB) = createValidSignedNodeInfo("TestB", requestStorage) // Update 2 as acceptedParametersUpdate

        // Put signed node info data
        nodeInfoStorage.putNodeInfo(signedNodeInfoA)
        val nodeInfoHashB = nodeInfoStorage.putNodeInfo(signedNodeInfoB)

        nodeInfoStorage.ackNodeInfoParametersUpdate(signedNodeInfoA.nodeInfo.legalIdentities[0].owningKey, networkParameters1.serialize().hash)
        // Update 2
        val networkParameters2 = testNetworkParameters(epoch = 2)
        networkMapStorage.saveNewParametersUpdate(networkParameters2, "Update 2", updateDeadline + 10.days)
        nodeInfoStorage.ackNodeInfoParametersUpdate(signedNodeInfoB.nodeInfo.legalIdentities[0].owningKey, networkParameters2.serialize().hash)
        val parameterUpdate = networkMapStorage.getCurrentParametersUpdate()!!
        networkMapStorage.switchFlagDay(parameterUpdate)
        // when
        val validNodeInfoHashes = networkMapStorage.getActiveNodeInfoHashes()
        // then
        assertThat(validNodeInfoHashes).containsOnly(nodeInfoHashB)
    }
}
