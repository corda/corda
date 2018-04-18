package net.corda.finance.flows

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalance
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.InProcessImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CashSelectionTest {

    @Test
    fun `unconsumed cash states`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val node = startNode().getOrThrow() as InProcessImpl
            val issuerRef = OpaqueBytes.of(0)
            val issuedAmount = 1000.DOLLARS

            node.rpc.startFlow(::CashIssueFlow, issuedAmount, issuerRef, defaultNotaryIdentity).returnValue.getOrThrow()

            val availableBalance = node.rpc.getCashBalance(issuedAmount.token)

            assertThat(availableBalance).isEqualTo(issuedAmount)

            val exitedAmount = 300.DOLLARS
            node.rpc.startFlow(::CashExitFlow, exitedAmount, issuerRef).returnValue.getOrThrow()

            val availableBalanceAfterExit = node.rpc.getCashBalance(issuedAmount.token)

            assertThat(availableBalanceAfterExit).isEqualTo(issuedAmount - exitedAmount)
        }
    }
}