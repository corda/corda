package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.CordaRuntimeException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.junit.Test
import kotlin.test.assertEquals

class RpcWorkerMultiIdentityTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val rpcUser = User("username", "password", permissions = setOf("ALL"))
            val combinedRpcHandle = startRpcFlowWorker(setOf(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME), listOf(rpcUser), 1).get()
            val bankC = startNode(providedName = DUMMY_BANK_C_NAME).get()

            val bankAProxy = CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, DUMMY_BANK_A_NAME).proxy
            val bankBProxy = CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, DUMMY_BANK_B_NAME).proxy

            val cashIssueResult = bankAProxy.startFlow(::CashIssueFlow, 11.POUNDS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            assertEquals(11.POUNDS, bankAProxy.getCashBalances()[GBP])

            val cashPayResult = bankAProxy.startFlow(::CashPaymentFlow, 8.POUNDS, bankC.nodeInfo.singleIdentity(), false).returnValue.get()
            assertEquals(3.POUNDS, bankAProxy.getCashBalances()[GBP])
            assertEquals(8.POUNDS, bankC.rpc.getCashBalances()[GBP])

            val cashPayResult2 = bankC.rpc.startFlow(::CashPaymentFlow, 2.POUNDS, bankBProxy.nodeInfo().singleIdentity()).returnValue.get()
            assertEquals(3.POUNDS, bankAProxy.getCashBalances()[GBP])
            assertEquals(6.POUNDS, bankC.rpc.getCashBalances()[GBP])
            // assertEquals(2.POUNDS, bankBProxy.getCashBalances()[GBP]) TODO: Investigate race condition, this condition sometimes passes and sometimes not

            Assertions.assertThatThrownBy {
                CordaRPCClient(combinedRpcHandle.rpcAddress).start(rpcUser.username, rpcUser.password, CHARLIE_NAME).proxy }.isInstanceOf(CordaRuntimeException::class.java)
        }
    }
}