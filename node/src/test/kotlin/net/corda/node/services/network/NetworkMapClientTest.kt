/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.serialization.serialize
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.internal.signWith
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals

class NetworkMapClientTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 100000.seconds

    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, PortAllocation.Incremental(10000).nextHostAndPort())
        val hostAndPort = server.start()
        networkMapClient = NetworkMapClient(URL("http://${hostAndPort.host}:${hostAndPort.port}"), DEV_ROOT_CA.certificate)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `registered node is added to the network map`() {
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(ALICE_NAME)

        networkMapClient.publish(signedNodeInfo)

        val nodeInfoHash = nodeInfo.serialize().sha256()

        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(nodeInfoHash)
        assertEquals(nodeInfo, networkMapClient.getNodeInfo(nodeInfoHash))

        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned(BOB_NAME)

        networkMapClient.publish(signedNodeInfo2)

        val nodeInfoHash2 = nodeInfo2.serialize().sha256()
        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(nodeInfoHash, nodeInfoHash2)
        assertEquals(cacheTimeout, networkMapClient.getNetworkMap().cacheMaxAge)
        assertEquals(nodeInfo2, networkMapClient.getNodeInfo(nodeInfoHash2))
    }

    @Test
    fun `errors return a meaningful error message`() {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val (_, aliceKey) = nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        nodeInfoBuilder.addLegalIdentity(BOB_NAME)
        val nodeInfo3 = nodeInfoBuilder.build()
        val signedNodeInfo3 = nodeInfo3.signWith(listOf(aliceKey))

        assertThatThrownBy { networkMapClient.publish(signedNodeInfo3) }
                .isInstanceOf(IOException::class.java)
                .hasMessage("Response Code 403: Missing signatures. Found 1 expected 2")
    }

    @Test
    fun `download NetworkParameters correctly`() {
        // The test server returns same network parameter for any hash.
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }

    @Test
    fun `get hostname string from http response correctly`() {
        assertEquals("test.host.name", networkMapClient.myPublicHostname())
    }

    @Test
    fun `handle parameters update`() {
        val nextParameters = testNetworkParameters(epoch = 2)
        val originalNetworkParameterHash = server.networkParameters.serialize().hash
        val nextNetworkParameterHash = nextParameters.serialize().hash
        val description = "Test parameters"
        server.scheduleParametersUpdate(nextParameters, description, Instant.now().plus(1, ChronoUnit.DAYS))
        val (networkMap) = networkMapClient.getNetworkMap()
        assertEquals(networkMap.networkParameterHash, originalNetworkParameterHash)
        assertEquals(networkMap.parametersUpdate?.description, description)
        assertEquals(networkMap.parametersUpdate?.newParametersHash, nextNetworkParameterHash)
        assertEquals(networkMapClient.getNetworkParameters(originalNetworkParameterHash).verified(), server.networkParameters)
        assertEquals(networkMapClient.getNetworkParameters(nextNetworkParameterHash).verified(), nextParameters)
        val keyPair = Crypto.generateKeyPair()
        val signedHash = nextNetworkParameterHash.serialize().sign(keyPair)
        networkMapClient.ackNetworkParametersUpdate(signedHash)
        assertEquals(nextNetworkParameterHash, server.latestParametersAccepted(keyPair.public))
    }
}
