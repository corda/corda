package net.corda.node.services.network

import net.corda.core.crypto.SignedData
import net.corda.core.internal.list
import net.corda.core.internal.readAll
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.SerializationEnvironmentRule
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
import kotlin.test.assertEquals

class NetworkMapTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    private val cacheTimeout = 1.seconds
    private val portAllocation = PortAllocation.Incremental(10000)

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = CompatibilityZoneParams(URL("http://$address"))
    }

    @After
    fun cleanUp() {
        networkMapServer.close()
    }

    @Test
    fun `node correctly downloads and saves network parameters file on startup`() {
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone, initialiseSerialization = false) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val networkParameters = alice.configuration.baseDirectory
                    .list { paths -> paths.filter { it.fileName.toString() == NETWORK_PARAMS_FILE_NAME }.findFirst().get() }
                    .readAll()
                    .deserialize<SignedData<NetworkParameters>>()
                    .verified()
            assertEquals(NetworkMapServer.stubNetworkParameter, networkParameters)
        }
    }

    @Test
    fun `nodes can see each other using the http network map`() {
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone, initialiseSerialization = false) {
            val alice = startNode(providedName = ALICE_NAME)
            val bob = startNode(providedName = BOB_NAME)
            val notaryNode = defaultNotaryNode.get()
            val aliceNode = alice.get()
            val bobNode = bob.get()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map add updates correctly when adding new node to network map`() {
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone, initialiseSerialization = false) {
            val alice = startNode(providedName = ALICE_NAME)
            val notaryNode = defaultNotaryNode.get()
            val aliceNode = alice.get()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)
            val bob = startNode(providedName = BOB_NAME)
            val bobNode = bob.get()

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map remove updates correctly`() {
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone, initialiseSerialization = false) {
            val alice = startNode(providedName = ALICE_NAME)
            val bob = startNode(providedName = BOB_NAME)
            val notaryNode = defaultNotaryNode.get()
            val aliceNode = alice.get()
            val bobNode = bob.get()

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

    private fun NodeHandle.onlySees(vararg nodes: NodeInfo) = assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
}
