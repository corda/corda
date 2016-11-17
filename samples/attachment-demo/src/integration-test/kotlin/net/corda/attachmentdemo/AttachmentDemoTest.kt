package net.corda.attachmentdemo

import net.corda.core.crypto.toBase58String
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
            val nodeA = startNode("Bank A").get()
            val nodeAApiAddr = nodeA.config.getHostAndPort("webAddress")
            val nodeBApiAddr = startNode("Bank B").get().config.getHostAndPort("webAddress")

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
