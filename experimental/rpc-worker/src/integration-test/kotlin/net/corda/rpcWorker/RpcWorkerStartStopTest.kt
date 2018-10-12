package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.flowworker.logMemoryStats
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import org.junit.Test


class RpcWorkerStartStopTest {

    companion object {
        private val log = contextLogger()
    }

    @Test
    fun startStop() {

        log.logMemoryStats("Very beginning")

        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser), 1).get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            val cashIssueResult = bankAProxy.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            println(cashIssueResult)
            println(bankAProxy.getCashBalances())
        }

        log.logMemoryStats("Between restarts")

        // Starting brand new instance
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser), 1).get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            val cashIssueResult = bankAProxy.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            println(cashIssueResult)
            println(bankAProxy.getCashBalances())
        }

        log.logMemoryStats("Very end")
    }
}