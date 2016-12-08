package net.corda.attachmentdemo

import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.getHostAndPort
import org.junit.Test
import kotlin.concurrent.thread

class AttachmentDemoTest {
    @Test fun `runs attachment demo`() {
        driver(dsl = {
            startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.Companion.type)))
            val nodeAFuture = startNode("Bank A")
            val nodeBApiAddr = startNode("Bank B").getOrThrow().config.getHostAndPort("webAddress")

            val nodeA = nodeAFuture.getOrThrow()
            val nodeAApiAddr = nodeA.config.getHostAndPort("webAddress")

            var recipientReturn: Boolean? = null
            var senderReturn: Boolean? = null
            val recipientThread = thread {
                recipientReturn = AttachmentDemoClientApi(nodeAApiAddr).runRecipient()
            }
            val senderThread = thread {
                val counterpartyKey = nodeA.nodeInfo.legalIdentity.owningKey.toBase58String()
                senderReturn = AttachmentDemoClientApi(nodeBApiAddr).runSender(counterpartyKey)
            }
            recipientThread.join()
            senderThread.join()

            assert(recipientReturn == true)
            assert(senderReturn == true)
        }, isDebug = true)
    }
}
