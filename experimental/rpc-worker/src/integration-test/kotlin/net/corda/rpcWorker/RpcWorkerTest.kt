package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalances
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.Test

class RpcWorkerTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser), 1).get()
            val bankB = startNode(providedName = DUMMY_BANK_B_NAME).get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            val cashIssueResult = bankAProxy.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            println(cashIssueResult)
            println(bankAProxy.getCashBalances())
            val cashPayResult = bankAProxy.startFlow(::CashPaymentFlow, 2.DOLLARS, bankB.nodeInfo.singleIdentity(), false).returnValue.get()
            println(cashPayResult)
            println(bankAProxy.getCashBalances())
        }
    }

}