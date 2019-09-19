package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalances
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions
import org.junit.Test
import kotlin.test.assertEquals

class RpcWorkerMultiIdentityTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val rpcUser = User("username", "password", permissions = setOf("ALL"))
            val combinedRpcHandle = startRpcFlowWorker(setOf(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME), listOf(rpcUser), 1).get()
            val bankC = startNode(providedName = DUMMY_BANK_C_NAME).get()

            val bankAProxy = CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, DUMMY_BANK_A_NAME).proxy
            val bankBProxy = CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, DUMMY_BANK_B_NAME).proxy

            bankAProxy.startFlow(::CashIssueFlow, 11.POUNDS, OpaqueBytes.of(0x01), defaultNotaryIdentity)
                    .returnValue.get()
            assertEquals(11.POUNDS, bankAProxy.getCashBalances()[GBP])

            bankAProxy.startFlow(::CashPaymentFlow, 8.POUNDS, bankC.nodeInfo.singleIdentity(), false).returnValue.get()
            assertEquals(3.POUNDS, bankAProxy.getCashBalances()[GBP])
            assertEquals(8.POUNDS, bankC.rpc.getCashBalances()[GBP])

            bankC.rpc.startFlow(::CashPaymentFlow, 2.POUNDS, bankBProxy.nodeInfo().singleIdentity()).returnValue.get()
            assertEquals(3.POUNDS, bankAProxy.getCashBalances()[GBP])
            assertEquals(6.POUNDS, bankC.rpc.getCashBalances()[GBP])
            // assertEquals(2.POUNDS, bankBProxy.getCashBalances()[GBP]) TODO: Investigate race condition, this condition sometimes passes and sometimes not

            Assertions.assertThatThrownBy {
                CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, CHARLIE_NAME).proxy
            }.isInstanceOf(CordaRuntimeException::class.java)
        }
    }
}