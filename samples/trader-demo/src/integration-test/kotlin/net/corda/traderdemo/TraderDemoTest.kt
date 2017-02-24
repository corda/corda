package net.corda.traderdemo

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.IssuerFlow
import net.corda.node.services.User
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.node.NodeBasedTest
import org.junit.Test

class TraderDemoTest : NodeBasedTest() {
    @Test
    fun `runs trader demo`() {
        val permissions = setOf(
                startFlowPermission<IssuerFlow.IssuanceRequester>(),
                startFlowPermission<net.corda.traderdemo.flow.SellerFlow>())
        val demoUser = listOf(User("demo", "demo", permissions))
        val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))
        val (nodeA, nodeB) = Futures.allAsList(
                startNode("Bank A", rpcUsers = demoUser),
                startNode("Bank B", rpcUsers = demoUser),
                startNode("BankOfCorda", rpcUsers = listOf(user)),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
        ).getOrThrow()

        val (nodeARpc, nodeBRpc) = listOf(nodeA, nodeB).map {
            val client = CordaRPCClient(it.configuration.rpcAddress!!)
            client.start(demoUser[0].username, demoUser[0].password).proxy()
        }

        TraderDemoClientApi(nodeARpc).runBuyer()
        TraderDemoClientApi(nodeBRpc).runSeller(counterparty = nodeA.info.legalIdentity.name)
    }
}
