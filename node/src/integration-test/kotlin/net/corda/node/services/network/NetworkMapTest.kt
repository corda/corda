package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.list
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.node.internal.*
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.URL
import java.time.Instant
import kotlin.streams.toList
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class NetworkMapTest(var initFunc: (URL, NetworkMapServer) -> CompatibilityZoneParams) {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 1.seconds
    private val portAllocation = RandomFree

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun runParams() = listOf(
                { addr: URL, nms: NetworkMapServer ->
                    SharedCompatibilityZoneParams(
                            addr,
                            publishNotaries = {
                                nms.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                            }
                    )
                },
                { addr: URL, nms: NetworkMapServer ->
                    SplitCompatibilityZoneParams(
                            doormanURL = URL("http://I/Don't/Exist"),
                            networkMapURL = addr,
                            publishNotaries = {
                                nms.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                            }
                    )
                }

        )
    }


    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = initFunc(URL("http://$address"), networkMapServer)
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
                    .readObject<SignedNetworkParameters>()
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

    @Test
    fun `test node heartbeat`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                systemProperties = mapOf("net.corda.node.internal.nodeinfo.publish.interval" to 1.seconds.toString())
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME).getOrThrow()
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNode.nodeInfo.serialize().hash)
            networkMapServer.removeNodeInfo(aliceNode.nodeInfo)
            assertThat(networkMapServer.networkMapHashes()).doesNotContain(aliceNode.nodeInfo.serialize().hash)
            Thread.sleep(2000)
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNode.nodeInfo.serialize().hash)
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
