package net.corda.attachmentdemo

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.transactions.SimpleNotaryService
import org.junit.Test
import java.util.concurrent.CompletableFuture

class AttachmentDemoTest {
    @Test fun `runs attachment demo`() {
        driver(dsl = {
            val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
            val (nodeA, nodeB) = Futures.allAsList(
                startNode("Bank A", rpcUsers = demoUser),
                startNode("Bank B", rpcUsers = demoUser),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.Companion.type)))
            ).getOrThrow()

            val senderThread = CompletableFuture.runAsync {
                nodeA.rpcClientToNode().use(demoUser[0].username, demoUser[0].password) {
                    sender(this)
                }
            }
            val recipientThread = CompletableFuture.runAsync {
                nodeB.rpcClientToNode().use(demoUser[0].username, demoUser[0].password) {
                    recipient(this)
                }
            }

            // Just check they don't throw any exceptions.d
            recipientThread.get()
            senderThread.get()
        }, isDebug = true)
    }
}
