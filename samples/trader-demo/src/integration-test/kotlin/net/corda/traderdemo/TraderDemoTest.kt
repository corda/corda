package net.corda.traderdemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.BOC
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.poll
import net.corda.testing.node.NodeBasedTest
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.CommercialPaperIssueFlow
import net.corda.traderdemo.flow.SellerFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.Executors

class TraderDemoTest : NodeBasedTest() {
    @Test
    fun `runs trader demo`() {
        val demoUser = User("demo", "demo", setOf(startFlowPermission<SellerFlow>()))
        val bankUser = User("user1", "test", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>(),
                startFlowPermission<CommercialPaperIssueFlow>()))
        val (nodeA, nodeB, bankNode) = listOf(
                startNode(DUMMY_BANK_A.name, rpcUsers = listOf(demoUser)),
                startNode(DUMMY_BANK_B.name, rpcUsers = listOf(demoUser)),
                startNode(BOC.name, rpcUsers = listOf(bankUser)),
                startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        ).transpose().getOrThrow()

        nodeA.registerInitiatedFlow(BuyerFlow::class.java)

        val (nodeARpc, nodeBRpc) = listOf(nodeA, nodeB).map {
            val client = CordaRPCClient(it.configuration.rpcAddress!!, initialiseSerialization = false)
            client.start(demoUser.username, demoUser.password).proxy
        }
        val nodeBankRpc = let {
            val client = CordaRPCClient(bankNode.configuration.rpcAddress!!, initialiseSerialization = false)
            client.start(bankUser.username, bankUser.password).proxy
        }

        val clientA = TraderDemoClientApi(nodeARpc)
        val clientB = TraderDemoClientApi(nodeBRpc)
        val clientBank = TraderDemoClientApi(nodeBankRpc)

        val originalACash = clientA.cashCount // A has random number of issued amount
        val expectedBCash = clientB.cashCount + 1
        val expectedPaper = listOf(clientA.commercialPaperCount + 1, clientB.commercialPaperCount)

        // TODO: Enable anonymisation
        clientBank.runIssuer(amount = 100.DOLLARS, buyerName = nodeA.info.legalIdentity.name, sellerName = nodeB.info.legalIdentity.name)
        clientB.runSeller(buyerName = nodeA.info.legalIdentity.name, amount = 5.DOLLARS)

        assertThat(clientA.cashCount).isGreaterThan(originalACash)
        assertThat(clientB.cashCount).isEqualTo(expectedBCash)
        // Wait until A receives the commercial paper
        val executor = Executors.newScheduledThreadPool(1)
        poll(executor, "A to be notified of the commercial paper", pollInterval = 100.millis) {
            val actualPaper = listOf(clientA.commercialPaperCount, clientB.commercialPaperCount)
            if (actualPaper == expectedPaper) Unit else null
        }.getOrThrow()
        executor.shutdown()
        assertThat(clientA.dollarCashBalance).isEqualTo(95.DOLLARS)
        assertThat(clientB.dollarCashBalance).isEqualTo(5.DOLLARS)
    }
}
