package net.corda.node.services.network

import net.corda.core.node.NodeInfo
import net.corda.core.utilities.seconds
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URL

class NetworkMapClientTest {
    private val portAllocation = PortAllocation.Incremental(10000)

    @Test
    fun `nodes can see each other using the http network map`() {
        NetworkMapServer(1.seconds, portAllocation.nextHostAndPort()).use {
            val (host, port) = it.start()
            driver(portAllocation = portAllocation, compatibilityZoneURL = URL("http://$host:$port")) {
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
    }

    @Test
    fun `nodes process network map add updates correctly when adding new node to network map`() {
        NetworkMapServer(1.seconds, portAllocation.nextHostAndPort()).use {
            val (host, port) = it.start()
            driver(portAllocation = portAllocation, compatibilityZoneURL = URL("http://$host:$port")) {
                val alice = startNode(providedName = ALICE.name)
                val notaryNode = defaultNotaryNode.get()
                val aliceNode = alice.get()

                notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)
                aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo)

                val bob = startNode(providedName = BOB.name)
                val bobNode = bob.get()

                // Wait for network map client to poll for the next update.
                Thread.sleep(2.seconds.toMillis())

                bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
                notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
                aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
            }
        }
    }

    @Test
    fun `nodes process network map remove updates correctly`() {
        NetworkMapServer(1.seconds, portAllocation.nextHostAndPort()).use {
            val (host, port) = it.start()
            driver(portAllocation = portAllocation, compatibilityZoneURL = URL("http://$host:$port")) {
                val alice = startNode(providedName = ALICE.name)
                val bob = startNode(providedName = BOB.name)

                val notaryNode = defaultNotaryNode.get()
                val aliceNode = alice.get()
                val bobNode = bob.get()

                notaryNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
                aliceNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)
                bobNode.onlySees(notaryNode.nodeInfo, aliceNode.nodeInfo, bobNode.nodeInfo)

                it.removeNodeInfo(aliceNode.nodeInfo)

                // Wait for network map client to poll for the next update.
                Thread.sleep(2.seconds.toMillis())

                notaryNode.onlySees(notaryNode.nodeInfo, bobNode.nodeInfo)
                bobNode.onlySees(notaryNode.nodeInfo, bobNode.nodeInfo)
            }
        }
    }

    private fun NodeHandle.onlySees(vararg nodes: NodeInfo) = assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
}
