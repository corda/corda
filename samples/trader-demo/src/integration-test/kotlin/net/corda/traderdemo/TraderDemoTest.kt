package net.corda.traderdemo

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.IssuerFlow
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import org.junit.Test

class TraderDemoTest {
    @Test fun `runs trader demo`() {
        driver(dsl = {
            val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))
            val (nodeA, nodeB) = Futures.allAsList(
                startNode("Bank A"),
                startNode("Bank B"),
                startNode("BankOfCorda", rpcUsers = listOf(user)),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
            ).getOrThrow()

            assert(TraderDemoClientApi(nodeA.configuration.webAddress).runBuyer())
            assert(TraderDemoClientApi(nodeB.configuration.webAddress).runSeller(counterparty = nodeA.nodeInfo.legalIdentity.name))
        }, isDebug = true)
    }
}
