package net.corda.node.services.network

import net.corda.core.node.NodeInfo
import net.corda.core.utilities.seconds
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.driver.CompatibilityZoneParams
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internalDriver
import net.corda.testing.node.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URL

// TODO There is a unit test class with the same name. Rename this to something else.
class NetworkMapClientTest {
    private val cacheTimeout = 1.seconds
    private val portAllocation = PortAllocation.Incremental(10000)

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = CompatibilityZoneParams(URL("http://$address"), rootCert = null)
    }

    @After
    fun cleanUp() {
        networkMapServer.close()
    }

    @Test
    fun `nodes can see each other using the http network map`() {
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone) {
            val alice = startNode(providedName = ALICE.name)
            val bob = startNode(providedName = BOB.name)

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
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone) {
            val alice = startNode(providedName = ALICE.name)
            val notaryNode = defaultNotaryNode.get()
            val aliceNode = alice.get()

            notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)
            aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)

            val bob = startNode(providedName = BOB.name)
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
        internalDriver(portAllocation = portAllocation, compatibilityZone = compatibilityZone) {
            val alice = startNode(providedName = ALICE.name)
            val bob = startNode(providedName = BOB.name)

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
