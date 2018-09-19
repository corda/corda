package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import org.junit.Test
import kotlin.test.assertEquals

class RpcWorkerPaidTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser)).get()
            val bankB = startNode().get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            val cashIssueResult = bankB.rpc.startFlow(::CashIssueFlow, 10.POUNDS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            assertEquals(10.POUNDS, bankB.rpc.getCashBalances()[GBP])

            val cashPayResult = bankB.rpc.startFlow(::CashPaymentFlow, 2.POUNDS, bankAProxy.nodeInfo().singleIdentity(), false).returnValue.get()
            assertEquals(8.POUNDS, bankB.rpc.getCashBalances()[GBP])
            assertEquals(2.POUNDS, bankAProxy.getCashBalances()[GBP])
        }
    }

}