package net.corda.attachmentdemo

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import org.junit.Test
import java.util.concurrent.CompletableFuture

class AttachmentDemoTest {
    // run with the default bank-of-london-cp.jar (~70KB).
    @Test fun `runs attachment demo with a 70KB jar file`() {
        attachmentDemo(0)
    }

    // run with a 10,000,000 bytes zip file. In practice, a slightly bigger in-memory file will be used (~10,050,000 bytes).
    @Test fun `runs attachment demo with a 10MB zip file`() {
        attachmentDemo(10000000)
    }

    // If invoked with numOfExpectedBytes <=0, it will run with the default bank-of-london-cp.jar.
    // if numOfExpectedBytes > 0 an in-memory zip file will be used as InputStream, with a size slightly bigger than numOfExpectedBytes.
    private fun attachmentDemo(numOfExpectedBytes: Int) {
        driver(dsl = {
            val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
            val (nodeA, nodeB) = Futures.allAsList(
                startNode("Bank A", rpcUsers = demoUser),
                startNode("Bank B", rpcUsers = demoUser),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.Companion.type)))
            ).getOrThrow()

            val senderThread = CompletableFuture.supplyAsync {
                nodeA.rpcClientToNode().use(demoUser[0].username, demoUser[0].password) {
                    sender(this, numOfExpectedBytes)
                }
            }.exceptionally { it.printStackTrace() }

            val recipientThread = CompletableFuture.supplyAsync{
                nodeB.rpcClientToNode().use(demoUser[0].username, demoUser[0].password) {
                    recipient(this)
                }
            }.exceptionally { it.printStackTrace() }

            // Just check they finish and don't throw any exceptions.
            senderThread.get()
            recipientThread.get()
        }, isDebug = true)
    }
}
