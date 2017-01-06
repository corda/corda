package net.corda.attachmentdemo

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import org.junit.Test
import kotlin.concurrent.thread

class AttachmentDemoTest {
    @Test fun `runs attachment demo`() {
        driver(dsl = {
            val (nodeA, nodeB) = Futures.allAsList(
                startNode("Bank A"),
                startNode("Bank B"),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.Companion.type)))
            ).getOrThrow()

            var recipientReturn: Boolean? = null
            var senderReturn: Boolean? = null
            val recipientThread = thread {
                recipientReturn = AttachmentDemoClientApi(nodeA.configuration.webAddress).runRecipient()
            }
            val senderThread = thread {
                val counterpartyKey = nodeA.nodeInfo.legalIdentity.owningKey.toBase58String()
                senderReturn = AttachmentDemoClientApi(nodeB.configuration.webAddress).runSender(counterpartyKey)
            }
            recipientThread.join()
            senderThread.join()

            assert(recipientReturn == true)
            assert(senderReturn == true)
        }, isDebug = true)
    }
}
