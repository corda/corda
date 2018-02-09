package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.time.Instant
import kotlin.streams.toList
import kotlin.test.assertEquals

class NetworkMapTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 1.seconds
    private val portAllocation = PortAllocation.RandomFree

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = CompatibilityZoneParams(URL("http://$address"), publishNotaries = {
            networkMapServer.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
        })
    }

    @After
    fun cleanUp() {
        networkMapServer.close()
    }

    @Test
    fun `node correctly downloads and saves network parameters file on startup`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val networkParameters = (alice.baseDirectory / NETWORK_PARAMS_FILE_NAME)
                    .readAll()
                    .deserialize<SignedDataWithCert<NetworkParameters>>()
                    .verified()
            // We use a random modified time above to make the network parameters unqiue so that we're sure they came
            // from the server
            assertEquals(networkMapServer.networkParameters, networkParameters)
        }
    }

    @Test
    fun `nodes can see each other using the http network map`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false
        ) {
            val (aliceNode, bobNode, notaryNode) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME),
                    defaultNotaryNode
            ).transpose().getOrThrow()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map add updates correctly when adding new node to network map`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false
        ) {
            val (aliceNode, notaryNode) = listOf(
                    startNode(providedName = ALICE_NAME),
                    defaultNotaryNode
            ).transpose().getOrThrow()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)

            val bobNode = startNode(providedName = BOB_NAME).getOrThrow()

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map remove updates correctly`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false
        ) {
            val (aliceNode, bobNode, notaryNode) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME),
                    defaultNotaryNode
            ).transpose().getOrThrow()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)

            networkMapServer.removeNodeInfo(aliceNode.nodeInfo)

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            notaryNode.onlySees(notaryNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(notaryNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    private fun NodeHandle.onlySees(vararg nodes: NodeInfo) {
        // Make sure the nodes aren't getting the node infos from their additional directories
        val nodeInfosDir = baseDirectory / CordformNode.NODE_INFO_DIRECTORY
        if (nodeInfosDir.exists()) {
            assertThat(nodeInfosDir.list { it.toList() }).isEmpty()
        }
        assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
    }
}
