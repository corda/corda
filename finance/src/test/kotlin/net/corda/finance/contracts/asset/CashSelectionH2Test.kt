package net.corda.finance.contracts.asset

import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashException
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test


class CashSelectionH2Test {

    @Test
    fun `check does not hold connection over retries`() {
        val mockNet = MockNetwork(threadPerNode = true)
        try {
            val notaryNode = mockNet.createNotaryNode()
            val bankA = mockNet.createNode(configOverrides = { existingConfig ->
                // Tweak connections to be minimal to make this easier (1 results in a hung node during start up, so use 2 connections).
                existingConfig.dataSourceProperties.setProperty("maximumPoolSize", "2")
                existingConfig
            })

            mockNet.startNodes()

            // Start more cash spends than we have connections.  If spend leaks a connection on retry, we will run out of connections.
            val flow1 = bankA.services.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notaryNode.info.chooseIdentity()))
            val flow2 = bankA.services.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notaryNode.info.chooseIdentity()))
            val flow3 = bankA.services.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notaryNode.info.chooseIdentity()))

            assertThatThrownBy { flow1.resultFuture.getOrThrow() }.isInstanceOf(CashException::class.java)
            assertThatThrownBy { flow2.resultFuture.getOrThrow() }.isInstanceOf(CashException::class.java)
            assertThatThrownBy { flow3.resultFuture.getOrThrow() }.isInstanceOf(CashException::class.java)
        } finally {
            mockNet.stopNodes()
        }
    }
}