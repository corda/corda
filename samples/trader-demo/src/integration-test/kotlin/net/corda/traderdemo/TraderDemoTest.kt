package net.corda.traderdemo

import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.getHostAndPort
import org.junit.Test

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        driver(dsl = {
            startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
            val nodeA = startNode("Bank A").get()
            val nodeAApiAddr = nodeA.config.getHostAndPort("webAddress")
            val nodeBApiAddr = startNode("Bank B").get().config.getHostAndPort("webAddress")

            assert(TraderDemoClientApi(nodeAApiAddr).runBuyer())
            assert(TraderDemoClientApi(nodeBApiAddr).runSeller(counterparty = nodeA.nodeInfo.legalIdentity.name))
        }, isDebug = true)
    }
}
