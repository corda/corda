package com.r3.corda.enterprise.perftestcordapp.flows

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.node.services.Permissions
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Ignore
import org.junit.Test

@Ignore("Use to test no-selection locally")
class NoSelectionIntegrationTest {

    @Test
    fun `single pay no selection`() {
        val aliceUser = User("A", "A", setOf(Permissions.startFlow<CashIssueAndPaymentNoSelection>()))
        driver(DriverParameters(
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("com.r3.corda.enterprise.perftestcordapp"),
                portAllocation = PortAllocation.Incremental(20000)
        )) {
            val alice = startNode(rpcUsers = listOf(aliceUser)).get()
            CordaRPCClient(alice.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndPaymentNoSelection, 1.DOLLARS, OpaqueBytes.of(0), alice.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity).returnValue.getOrThrow()
            }
        }
    }

}
