package net.corda.node.services.network

import net.corda.core.crypto.sha256
import net.corda.core.serialization.serialize
import net.corda.core.utilities.seconds
import net.corda.node.services.network.TestNodeInfoFactory.createNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.node.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals

class NetworkMapClientTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

    companion object {
        private val cacheTimeout = 100000.seconds
    }

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, PortAllocation.RandomFree.nextHostAndPort())
        val hostAndPort = server.start()
        networkMapClient = NetworkMapClient(URL("http://${hostAndPort.host}:${hostAndPort.port}"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `registered node is added to the network map`() {
        // Create node info.
        val signedNodeInfo = createNodeInfo("Test1")
        val nodeInfo = signedNodeInfo.verified()

        networkMapClient.publish(signedNodeInfo)

        val nodeInfoHash = nodeInfo.serialize().sha256()

        assertThat(networkMapClient.getNetworkMap().networkMap).containsExactly(nodeInfoHash)
        assertEquals(nodeInfo, networkMapClient.getNodeInfo(nodeInfoHash))

        val signedNodeInfo2 = createNodeInfo("Test2")
        val nodeInfo2 = signedNodeInfo2.verified()
        networkMapClient.publish(signedNodeInfo2)

        val nodeInfoHash2 = nodeInfo2.serialize().sha256()
        assertThat(networkMapClient.getNetworkMap().networkMap).containsExactly(nodeInfoHash, nodeInfoHash2)
        assertEquals(cacheTimeout, networkMapClient.getNetworkMap().cacheMaxAge)
        assertEquals(nodeInfo2, networkMapClient.getNodeInfo(nodeInfoHash2))
    }

    @Test
    fun `get hostname string from http response correctly`() {
       assertEquals("test.host.name", networkMapClient.myPublicHostname())
    }
}
