package net.corda.traderdemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.packageName
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.schemas.CommercialPaperSchemaV1
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.driver.poll
import net.corda.testing.node.NodeBasedTest
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.CommercialPaperIssueFlow
import net.corda.traderdemo.flow.SellerFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.Executors

class TraderDemoTest : NodeBasedTest(listOf(
        "net.corda.finance.contracts.asset", "net.corda.finance.contracts",
        CashSchemaV1::class.packageName, CommercialPaperSchemaV1::class.packageName)) {
    @Test
    fun `runs trader demo`() {
        val demoUser = User("demo", "demo", setOf(startFlowPermission<SellerFlow>()))
        val bankUser = User("user1", "test", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>(),
                startFlowPermission<CommercialPaperIssueFlow>()))
        val notaryFuture = startNotaryNode(DUMMY_NOTARY.name, validating = false)
        val nodeAFuture = startNode(DUMMY_BANK_A.name, rpcUsers = listOf(demoUser))
        val nodeBFuture = startNode(DUMMY_BANK_B.name, rpcUsers = listOf(demoUser))
        val bankNodeFuture = startNode(BOC.name, rpcUsers = listOf(bankUser))
        val (nodeA, nodeB, bankNode) = listOf(nodeAFuture, nodeBFuture, bankNodeFuture, notaryFuture).map { it.getOrThrow() }

        nodeA.internals.registerInitiatedFlow(BuyerFlow::class.java)
        val (nodeARpc, nodeBRpc) = listOf(nodeA, nodeB).map {
            val client = CordaRPCClient(it.internals.configuration.rpcAddress!!)
            client.start(demoUser.username, demoUser.password).proxy
        }
        val nodeBankRpc = let {
            val client = CordaRPCClient(bankNode.internals.configuration.rpcAddress!!)
            client.start(bankUser.username, bankUser.password).proxy
        }

        val clientA = TraderDemoClientApi(nodeARpc)
        val clientB = TraderDemoClientApi(nodeBRpc)
        val clientBank = TraderDemoClientApi(nodeBankRpc)

        val originalACash = clientA.cashCount // A has random number of issued amount
        val expectedBCash = clientB.cashCount + 1
        val expectedPaper = listOf(clientA.commercialPaperCount + 1, clientB.commercialPaperCount)

        clientBank.runIssuer(amount = 100.DOLLARS, buyerName = nodeA.info.chooseIdentity().name, sellerName = nodeB.info.chooseIdentity().name)
        clientB.runSeller(buyerName = nodeA.info.chooseIdentity().name, amount = 5.DOLLARS)

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
