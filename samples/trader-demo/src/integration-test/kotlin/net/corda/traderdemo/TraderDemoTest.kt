package net.corda.traderdemo

import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.IssuerFlow
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.getHostAndPort
import org.junit.Test

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        driver(dsl = {
            startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
            val nodeA = startNode("Bank A").getOrThrow()
            val nodeAApiAddr = nodeA.config.getHostAndPort("webAddress")
            val nodeBApiAddr = startNode("Bank B").getOrThrow().config.getHostAndPort("webAddress")
            val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))
            startNode("BankOfCorda", rpcUsers = listOf(user)).getOrThrow()

            assert(TraderDemoClientApi(nodeAApiAddr).runBuyer())
            assert(TraderDemoClientApi(nodeBApiAddr).runSeller(counterparty = nodeA.nodeInfo.legalIdentity.name))
        }, isDebug = true)
    }
}
