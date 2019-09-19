package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalances
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.Test
import kotlin.test.assertEquals

class RpcWorkerPaidTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser)).get()
            val bankB = startNode().get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            bankB.rpc.startFlow(::CashIssueFlow, 10.POUNDS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            assertEquals(10.POUNDS, bankB.rpc.getCashBalances()[GBP])

            bankB.rpc.startFlow(::CashPaymentFlow, 2.POUNDS, bankAProxy.nodeInfo().singleIdentity(), false).returnValue.get()
            assertEquals(8.POUNDS, bankB.rpc.getCashBalances()[GBP])
            Thread.sleep(10000)
            // This can sometimes fail due to slow update. Similar to the RpcWorkerMultiIdentityTest, a timeout worked but the test should be re-written, perhaps to track the vault?
            assertEquals(2.POUNDS, bankAProxy.getCashBalances()[GBP])
        }
    }

}