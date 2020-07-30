package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.sign
import net.corda.core.serialization.serialize
import net.corda.core.utilities.seconds
import net.corda.node.VersionInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.coretesting.internal.TestNodeInfoBuilder
import net.corda.coretesting.internal.createNodeInfoAndSigned
import net.corda.coretesting.internal.signWith
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
        server = NetworkMapServer(cacheTimeout)
        val address = server.start()
        networkMapClient = NetworkMapClient(URL("http://$address"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
    fun `registered node is added to the network map v2`() {
        server.version = "2"
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(ALICE_NAME)

        networkMapClient.publish(signedNodeInfo)

        val nodeInfoHash = nodeInfo.serialize().sha256()

        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(nodeInfoHash)
        assertEquals(nodeInfo, networkMapClient.getNodeInfos().single())

        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned(BOB_NAME)

        networkMapClient.publish(signedNodeInfo2)

        val nodeInfoHash2 = nodeInfo2.serialize().sha256()
        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(nodeInfoHash, nodeInfoHash2)
        assertEquals(cacheTimeout, networkMapClient.getNetworkMap().cacheMaxAge)
        assertEquals("2", networkMapClient.getNetworkMap().serverVersion)
        assertThat(networkMapClient.getNodeInfos()).containsExactlyInAnyOrder(nodeInfo, nodeInfo2)
    }

    @Test(timeout=300_000)
	fun `negative test - registered invalid node is added to the network map`() {
        val invalidLongNodeName = CordaX500Name(
                commonName = "AB123456789012345678901234567890123456789012345678901234567890",
                organisationUnit = "AB123456789012345678901234567890123456789012345678901234567890",
                organisation = "Long Plc",
                locality = "AB123456789012345678901234567890123456789012345678901234567890",
                state = "AB123456789012345678901234567890123456789012345678901234567890",
                country= "IT")

        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(invalidLongNodeName)

        networkMapClient.publish(signedNodeInfo)

        val nodeInfoHash = nodeInfo.serialize().sha256()

        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(nodeInfoHash)
        assertEquals(nodeInfo, networkMapClient.getNodeInfo(nodeInfoHash))
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `download NetworkParameters correctly`() {
        // The test server returns same network parameter for any hash.
        val parametersHash = server.networkParameters.serialize().hash
        val networkParameters = networkMapClient.getNetworkParameters(parametersHash).verified()
        assertEquals(server.networkParameters, networkParameters)
    }

    @Test(timeout=300_000)
	fun `get hostname string from http response correctly`() {
        assertEquals("test.host.name", networkMapClient.myPublicHostname())
    }

    @Test(timeout=300_000)
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
