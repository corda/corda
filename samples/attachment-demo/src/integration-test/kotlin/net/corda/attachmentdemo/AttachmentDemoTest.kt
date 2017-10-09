package net.corda.attachmentdemo

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.concurrent.CompletableFuture.supplyAsync

class AttachmentDemoTest {
    // run with a 10,000,000 bytes in-memory zip file. In practice, a slightly bigger file will be used (~10,002,000 bytes).
    @Test
    fun `attachment demo using a 10MB zip file`() {
        val numOfExpectedBytes = 10_000_000
        driver(isDebug = true, portAllocation = PortAllocation.Incremental(20000)) {
            val demoUser = listOf(User("demo", "demo", setOf(startFlowPermission<AttachmentDemoFlow>())))
            val (nodeA, nodeB) = listOf(
                    startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser, maximumHeapSize = "1g"),
                    startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser, maximumHeapSize = "1g"),
                    startNotaryNode(DUMMY_NOTARY.name, validating = false))
                    .map { it.getOrThrow() }
            startWebserver(nodeB).getOrThrow()

            val senderThread = supplyAsync {
                nodeA.rpcClientToNode().start(demoUser[0].username, demoUser[0].password).use {
                    sender(it.proxy, numOfExpectedBytes)
                }
            }

            val recipientThread = supplyAsync {
                nodeB.rpcClientToNode().start(demoUser[0].username, demoUser[0].password).use {
                    recipient(it.proxy, nodeB.webAddress.port)
                }
            }

            senderThread.getOrThrow()
            recipientThread.getOrThrow()
        }
    }
}
