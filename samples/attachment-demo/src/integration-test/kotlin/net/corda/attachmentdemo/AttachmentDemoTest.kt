package net.corda.attachmentdemo

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.concurrent.CompletableFuture.supplyAsync

class AttachmentDemoTest {
    // run with a 10,000,000 bytes in-memory zip file. In practice, a slightly bigger file will be used (~10,002,000 bytes).
    @Test fun `attachment demo using a 10MB zip file`() {
        val numOfExpectedBytes = 10_000_000
        driver(dsl = {
            val demoUser = listOf(User("demo", "demo", setOf(startFlowPermission<AttachmentDemoFlow>())))
            val (nodeA, nodeB) = listOf(
                    startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser),
                    startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser),
                    startNode(providedName = DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type))))
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
        }, isDebug = true)
    }
}
