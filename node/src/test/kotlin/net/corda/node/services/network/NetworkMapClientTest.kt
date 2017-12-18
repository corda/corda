package net.corda.node.services.network

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.cert
import net.corda.core.serialization.serialize
import net.corda.core.utilities.seconds
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.DEV_TRUST_ROOT
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        networkMapClient = NetworkMapClient(URL("http://${hostAndPort.host}:${hostAndPort.port}"), DEV_TRUST_ROOT.cert)
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

        assertThat(networkMapClient.getNetworkMap().networkMap.nodeInfoHashes).containsExactly(nodeInfoHash)
        assertEquals(nodeInfo, networkMapClient.getNodeInfo(nodeInfoHash))

        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned(BOB_NAME)

        networkMapClient.publish(signedNodeInfo2)

        val nodeInfoHash2 = nodeInfo2.serialize().sha256()
        assertThat(networkMapClient.getNetworkMap().networkMap.nodeInfoHashes).containsExactly(nodeInfoHash, nodeInfoHash2)
        assertEquals(cacheTimeout, networkMapClient.getNetworkMap().cacheMaxAge)
        assertEquals(nodeInfo2, networkMapClient.getNodeInfo(nodeInfoHash2))
    }


    @Test
    fun `download NetworkParameter correctly`() {
        // The test server returns same network parameter for any hash.
        val networkParameter = networkMapClient.getNetworkParameter(SecureHash.randomSHA256())?.verified()
        assertNotNull(networkParameter)
        assertEquals(NetworkMapServer.stubNetworkParameter, networkParameter)
    }

    @Test
    fun `get hostname string from http response correctly`() {
        assertEquals("test.host.name", networkMapClient.myPublicHostname())
    }
}
