package net.corda.attachmentdemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DummyClusterSpec
import net.corda.testing.node.internal.findCordapp
import org.junit.Test
import java.util.concurrent.CompletableFuture.supplyAsync

class AttachmentDemoTest {
    // run with a 10,000,000 bytes in-memory zip file. In practice, a slightly bigger file will be used (~10,002,000 bytes).
    @Test(timeout=300_000)
	fun `attachment demo using a 10MB zip file`() {
        val numOfExpectedBytes = 10_000_000
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(findCordapp("net.corda.attachmentdemo.contracts"), findCordapp("net.corda.attachmentdemo.workflows")),
                notarySpecs = listOf(NotarySpec(name = DUMMY_NOTARY_NAME, cluster = DummyClusterSpec(clusterSize = 1))))
        ) {
            val demoUser = listOf(User("demo", "demo", setOf(all())))
            val (nodeA, nodeB) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = demoUser, maximumHeapSize = "1g"),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = demoUser, maximumHeapSize = "1g")
            ).map { it.getOrThrow() }
            val webserverHandle = startWebserver(nodeB).getOrThrow()

            val senderThread = supplyAsync {
                CordaRPCClient(nodeA.rpcAddress).start(demoUser[0].username, demoUser[0].password).use {
                    sender(it.proxy, numOfExpectedBytes)
                }
            }

            val recipientThread = supplyAsync {
                CordaRPCClient(nodeB.rpcAddress).start(demoUser[0].username, demoUser[0].password).use {
                    recipient(it.proxy, webserverHandle.listenAddress.port)
                }
            }

            senderThread.getOrThrow()
            recipientThread.getOrThrow()
        }
    }
}
