package net.corda.attachmentdemo

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.nodeapi.ServiceInfo
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
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
            val notaryFuture = startNode(providedName = DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
            val nodeAFuture = startNode(providedName = DUMMY_BANK_A.name, rpcUsers = demoUser)
            val nodeBFuture = startNode(providedName = DUMMY_BANK_B.name, rpcUsers = demoUser)
            val (nodeA, nodeB) = listOf(nodeAFuture, nodeBFuture, notaryFuture).map { it.getOrThrow() }

            val senderThread = supplyAsync {
                nodeA.rpcClientToNode().start(demoUser[0].username, demoUser[0].password).use {
                    sender(it.proxy, numOfExpectedBytes)
                }
            }.exceptionally { it.printStackTrace() }

            val recipientThread = supplyAsync {
                nodeB.rpcClientToNode().start(demoUser[0].username, demoUser[0].password).use {
                    recipient(it.proxy)
                }
            }.exceptionally { it.printStackTrace() }

            // Just check they finish and don't throw any exceptions.
            senderThread.get()
            recipientThread.get()
        }, isDebug = true)
    }
}
